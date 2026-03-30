package org.remus.resticexplorer.scanning.web;

import lombok.RequiredArgsConstructor;
import org.remus.resticexplorer.repository.RepositoryService;
import org.remus.resticexplorer.repository.data.ResticRepository;
import org.remus.resticexplorer.scanning.CheckService;
import org.remus.resticexplorer.scanning.ScanService;
import org.remus.resticexplorer.scanning.data.CheckResult;
import org.remus.resticexplorer.scanning.data.ScanResult;
import org.remus.resticexplorer.scanning.web.dto.ActiveJobDto;
import org.remus.resticexplorer.scanning.web.dto.RepositoryStatusDto;
import org.remus.resticexplorer.scanning.web.dto.StatusResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class StatusApiController {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final RepositoryService repositoryService;
    private final ScanService scanService;
    private final CheckService checkService;

    @GetMapping("/status")
    public StatusResponse getStatus() {
        List<ResticRepository> repos = repositoryService.findAll();
        Map<Long, RepositoryStatusDto> repoStatuses = new LinkedHashMap<>();
        List<ActiveJobDto> activeJobs = new ArrayList<>();

        boolean anyFailed = false;
        boolean anyInProgress = false;
        boolean allSuccess = !repos.isEmpty();
        int scannedCount = 0;

        for (ResticRepository repo : repos) {
            Optional<ScanResult> lastScan = scanService.getLastScanResult(repo.getId());
            Optional<CheckResult> lastCheck = checkService.getLastCheckResult(repo.getId());

            String scanStatus = lastScan.map(r -> r.getStatus().name()).orElse("PENDING");
            String checkStatus = lastCheck.map(r -> r.getStatus().name()).orElse(null);
            long snapshotCount = scanService.getSnapshotCount(repo.getId());
            String lastScanned = repo.getLastScanned() != null
                    ? repo.getLastScanned().format(FORMATTER) : null;

            repoStatuses.put(repo.getId(), new RepositoryStatusDto(
                    scanStatus, checkStatus, snapshotCount, lastScanned));

            // Track active jobs
            if (lastScan.isPresent() && lastScan.get().getStatus() == ScanResult.ScanStatus.IN_PROGRESS) {
                activeJobs.add(new ActiveJobDto(repo.getId(), repo.getName(), "SCAN"));
                anyInProgress = true;
            }
            if (lastCheck.isPresent() && lastCheck.get().getStatus() == CheckResult.CheckStatus.IN_PROGRESS) {
                activeJobs.add(new ActiveJobDto(repo.getId(), repo.getName(), "CHECK"));
                anyInProgress = true;
            }

            // Compute overall status
            if (lastScan.isPresent()) {
                scannedCount++;
                if (lastScan.get().getStatus() == ScanResult.ScanStatus.FAILED) {
                    anyFailed = true;
                    allSuccess = false;
                } else if (lastScan.get().getStatus() != ScanResult.ScanStatus.SUCCESS) {
                    allSuccess = false;
                }
            } else {
                allSuccess = false;
            }
        }

        String overallStatus;
        if (repos.isEmpty()) {
            overallStatus = "none";
        } else if (anyFailed) {
            overallStatus = "failed";
        } else if (anyInProgress) {
            overallStatus = "scanning";
        } else if (allSuccess && scannedCount == repos.size()) {
            overallStatus = "ok";
        } else {
            overallStatus = "pending";
        }

        return new StatusResponse(overallStatus, repoStatuses, activeJobs);
    }
}
