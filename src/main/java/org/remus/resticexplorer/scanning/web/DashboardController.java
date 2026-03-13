package org.remus.resticexplorer.scanning.web;

import lombok.RequiredArgsConstructor;
import org.remus.resticexplorer.repository.GroupService;
import org.remus.resticexplorer.repository.RepositoryService;
import org.remus.resticexplorer.repository.data.RepositoryGroup;
import org.remus.resticexplorer.repository.data.ResticRepository;
import org.remus.resticexplorer.scanning.ScanService;
import org.remus.resticexplorer.scanning.data.ScanResult;
import org.remus.resticexplorer.scanning.data.Snapshot;
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
        return "scanning/dashboard";
    }

    @GetMapping("/repositories/{id}/snapshots")
    public String repositorySnapshots(@PathVariable Long id, Model model) {
        ResticRepository repo = repositoryService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + id));
        List<Snapshot> snapshots = scanService.getSnapshots(id);
        Optional<ScanResult> lastScan = scanService.getLastScanResult(id);

        model.addAttribute("repository", repo);
        model.addAttribute("snapshots", snapshots);
        model.addAttribute("lastScanResult", lastScan.orElse(null));
        return "scanning/snapshots";
    }

    @PostMapping("/repositories/{id}/scan")
    public String triggerScan(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            scanService.scanRepository(id);
            redirectAttributes.addFlashAttribute("successMessage", "Repository scanned successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Scan failed: " + e.getMessage());
        }
        return "redirect:/repositories/" + id + "/snapshots";
    }
}
