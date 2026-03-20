package org.remus.resticexplorer.scanning.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.remus.resticexplorer.config.exception.RepositoryNotFoundException;
import org.remus.resticexplorer.config.exception.SnapshotNotFoundException;
import org.remus.resticexplorer.repository.GroupService;
import org.remus.resticexplorer.repository.RepositoryService;
import org.remus.resticexplorer.repository.data.RepositoryGroup;
import org.remus.resticexplorer.repository.data.ResticRepository;
import org.remus.resticexplorer.scanning.CheckService;
import org.remus.resticexplorer.scanning.RetentionPolicyChecker;
import org.remus.resticexplorer.scanning.RetentionPolicyResult;
import org.remus.resticexplorer.scanning.ScanService;
import org.remus.resticexplorer.scanning.data.CheckResult;
import org.remus.resticexplorer.scanning.data.ScanResult;
import org.remus.resticexplorer.scanning.data.Snapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final RepositoryService repositoryService;
    private final ScanService scanService;
    private final CheckService checkService;
    private final GroupService groupService;
    private final ObjectMapper objectMapper;

    @GetMapping("/")
    public String dashboard(Model model, HttpServletRequest request) {
        List<ResticRepository> repos = repositoryService.findAll();
        Map<Long, Long> snapshotCounts = new HashMap<>();
        Map<Long, ScanResult> lastScanResults = new HashMap<>();
        Map<Long, CheckResult> lastCheckResults = new HashMap<>();

        for (ResticRepository repo : repos) {
            snapshotCounts.put(repo.getId(), scanService.getSnapshotCount(repo.getId()));
            scanService.getLastScanResult(repo.getId()).ifPresent(r -> lastScanResults.put(repo.getId(), r));
            checkService.getLastCheckResult(repo.getId()).ifPresent(r -> lastCheckResults.put(repo.getId(), r));
        }

        // Compute retention policy results from last scan result (stored violations)
        Map<Long, RetentionPolicyResult> retentionResults = new HashMap<>();
        for (ResticRepository repo : repos) {
            RetentionPolicyResult rpr = retentionResultFromScanResult(lastScanResults.get(repo.getId()));
            if (rpr != null) {
                retentionResults.put(repo.getId(), rpr);
            }
        }

        // Group repositories
        List<RepositoryGroup> groups = groupService.findAll();
        Map<Long, List<ResticRepository>> groupedRepos = repos.stream()
                .filter(r -> r.getGroup() != null)
                .collect(Collectors.groupingBy(r -> r.getGroup().getId()));
        List<ResticRepository> ungroupedRepos = repos.stream()
                .filter(r -> r.getGroup() == null)
                .collect(Collectors.toList());

        model.addAttribute("repositories", repos);
        model.addAttribute("groups", groups);
        model.addAttribute("groupedRepos", groupedRepos);
        model.addAttribute("ungroupedRepos", ungroupedRepos);
        model.addAttribute("snapshotCounts", snapshotCounts);
        model.addAttribute("lastScanResults", lastScanResults);
        model.addAttribute("lastCheckResults", lastCheckResults);
        model.addAttribute("retentionResults", retentionResults);
        model.addAttribute("totalRepositories", repos.size());
        model.addAttribute("totalSnapshots", scanService.getTotalSnapshotCount());
        model.addAttribute("collapsedGroups", parseCollapsedGroups(request));

        // Calculate overall status from scan results
        String overallStatus = "none"; // no repos
        if (!repos.isEmpty()) {
            boolean anyFailed = lastScanResults.values().stream()
                    .anyMatch(r -> r.getStatus() == ScanResult.ScanStatus.FAILED);
            boolean anyInProgress = lastScanResults.values().stream()
                    .anyMatch(r -> r.getStatus() == ScanResult.ScanStatus.IN_PROGRESS);
            boolean allSuccess = lastScanResults.size() == repos.size()
                    && !lastScanResults.isEmpty()
                    && lastScanResults.values().stream()
                    .allMatch(r -> r.getStatus() == ScanResult.ScanStatus.SUCCESS);

            if (anyFailed) {
                overallStatus = "failed";
            } else if (anyInProgress) {
                overallStatus = "scanning";
            } else if (allSuccess) {
                overallStatus = "ok";
            } else {
                overallStatus = "pending";
            }
        }
        model.addAttribute("overallStatus", overallStatus);

        return "scanning/dashboard";
    }

    @GetMapping("/repositories/{id}/snapshots")
    public String repositorySnapshots(@PathVariable Long id, Model model,
                                       @PageableDefault(size = 25, sort = "snapshotTime", direction = Sort.Direction.DESC) Pageable pageable) {
        ResticRepository repo = repositoryService.findById(id)
                .orElseThrow(() -> new RepositoryNotFoundException(id));
        Page<Snapshot> snapshotPage = scanService.getSnapshots(id, pageable);
        Optional<ScanResult> lastScan = scanService.getLastScanResult(id);
        Optional<CheckResult> lastCheck = checkService.getLastCheckResult(id);

        model.addAttribute("repository", repo);
        model.addAttribute("page", snapshotPage);
        model.addAttribute("lastScanResult", lastScan.orElse(null));

        // Retention policy result for snapshot page
        model.addAttribute("retentionResult", retentionResultFromScanResult(lastScan.orElse(null)));
        model.addAttribute("lastCheckResult", lastCheck.orElse(null));
        return "scanning/snapshots";
    }

    @GetMapping("/repositories/{repoId}/snapshots/{snapshotId}")
    public String snapshotDetail(@PathVariable Long repoId, @PathVariable String snapshotId, Model model) {
        ResticRepository repo = repositoryService.findById(repoId)
                .orElseThrow(() -> new RepositoryNotFoundException(repoId));
        Snapshot snapshot = scanService.getSnapshot(repoId, snapshotId)
                .orElseThrow(() -> new SnapshotNotFoundException(snapshotId));

        model.addAttribute("repository", repo);
        model.addAttribute("snapshot", snapshot);
        return "scanning/snapshot-detail";
    }

    @PostMapping("/repositories/{id}/scan")
    public String triggerScan(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            scanService.scanRepository(id);
            redirectAttributes.addFlashAttribute("successMessage", "message.scanSuccess");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Scan failed: " + e.getMessage());
        }
        return "redirect:/repositories/" + id + "/snapshots";
    }

    @PostMapping("/repositories/{id}/check")
    public String triggerCheck(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            checkService.checkRepository(id);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Integrity check failed: " + e.getMessage());
        }
        return "redirect:/repositories/" + id + "/snapshots";
    }

    private Set<String> parseCollapsedGroups(HttpServletRequest request) {
        if (request.getCookies() == null) return Collections.emptySet();
        for (Cookie cookie : request.getCookies()) {
            if ("restic_group_state".equals(cookie.getName())) {
                try {
                    String json = URLDecoder.decode(cookie.getValue(), StandardCharsets.UTF_8);
                    Map<String, String> state = objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
                    return state.entrySet().stream()
                            .filter(e -> "collapsed".equals(e.getValue()))
                            .map(Map.Entry::getKey)
                            .collect(Collectors.toSet());
                } catch (Exception e) {
                    // ignore malformed cookie
                }
                break;
            }
        }
        return Collections.emptySet();
    }

    private RetentionPolicyResult retentionResultFromScanResult(ScanResult scanResult) {
        if (scanResult == null || scanResult.getRetentionPolicyFulfilled() == null) {
            return null;
        }
        RetentionPolicyResult result = new RetentionPolicyResult();
        result.setFulfilled(scanResult.getRetentionPolicyFulfilled());
        String violations = scanResult.getRetentionPolicyViolations();
        if (violations != null && !violations.isBlank()) {
            result.setViolations(Arrays.asList(violations.split("\n")));
        }
        return result;
    }
}
