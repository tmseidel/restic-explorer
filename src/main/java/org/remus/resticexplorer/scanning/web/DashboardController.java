package org.remus.resticexplorer.scanning.web;

import lombok.RequiredArgsConstructor;
import org.remus.resticexplorer.config.exception.RepositoryNotFoundException;
import org.remus.resticexplorer.repository.GroupService;
import org.remus.resticexplorer.repository.RepositoryService;
import org.remus.resticexplorer.repository.data.RepositoryGroup;
import org.remus.resticexplorer.repository.data.ResticRepository;
import org.remus.resticexplorer.scanning.ScanService;
import org.remus.resticexplorer.scanning.data.ScanResult;
import org.remus.resticexplorer.scanning.data.Snapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final RepositoryService repositoryService;
    private final ScanService scanService;
    private final GroupService groupService;

    @GetMapping("/")
    public String dashboard(Model model) {
        List<ResticRepository> repos = repositoryService.findAll();
        Map<Long, Long> snapshotCounts = new HashMap<>();
        Map<Long, ScanResult> lastScanResults = new HashMap<>();

        for (ResticRepository repo : repos) {
            snapshotCounts.put(repo.getId(), scanService.getSnapshotCount(repo.getId()));
            scanService.getLastScanResult(repo.getId()).ifPresent(r -> lastScanResults.put(repo.getId(), r));
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
        model.addAttribute("totalRepositories", repos.size());
        model.addAttribute("totalSnapshots", scanService.getTotalSnapshotCount());

        // Calculate overall status from scan results
        String overallStatus = "none"; // no repos
        if (!repos.isEmpty()) {
            boolean anyFailed = lastScanResults.values().stream()
                    .anyMatch(r -> r.getStatus() == ScanResult.ScanStatus.FAILED);
            boolean anyInProgress = lastScanResults.values().stream()
                    .anyMatch(r -> r.getStatus() == ScanResult.ScanStatus.IN_PROGRESS);
            boolean allSuccess = !lastScanResults.isEmpty() && lastScanResults.values().stream()
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

        model.addAttribute("repository", repo);
        model.addAttribute("page", snapshotPage);
        model.addAttribute("lastScanResult", lastScan.orElse(null));
        return "scanning/snapshots";
    }

    @GetMapping("/repositories/{repoId}/snapshots/{snapshotId}")
    public String snapshotDetail(@PathVariable Long repoId, @PathVariable String snapshotId, Model model) {
        ResticRepository repo = repositoryService.findById(repoId)
                .orElseThrow(() -> new RepositoryNotFoundException(repoId));
        Snapshot snapshot = scanService.getSnapshot(repoId, snapshotId)
                .orElseThrow(() -> new RepositoryNotFoundException(repoId));

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
}
