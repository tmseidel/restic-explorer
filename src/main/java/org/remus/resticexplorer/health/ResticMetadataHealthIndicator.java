package org.remus.resticexplorer.health;

import lombok.RequiredArgsConstructor;
import org.remus.resticexplorer.repository.RepositoryService;
import org.remus.resticexplorer.repository.data.ResticRepository;
import org.remus.resticexplorer.scanning.CheckService;
import org.remus.resticexplorer.scanning.ScanService;
import org.remus.resticexplorer.scanning.data.CheckResult;
import org.remus.resticexplorer.scanning.data.ScanResult;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ResticMetadataHealthIndicator implements HealthIndicator {

    private final RepositoryService repositoryService;
    private final ScanService scanService;
    private final CheckService checkService;

    @Override
    public Health health() {
        List<ResticRepository> repos = repositoryService.findAll();
        long totalSnapshots = scanService.getTotalSnapshotCount();
        int failedRepos = 0;
        int successRepos = 0;

        Map<String, Object> details = new HashMap<>();
        details.put("totalRepositories", repos.size());
        details.put("totalCachedSnapshots", totalSnapshots);

        for (ResticRepository repo : repos) {
            Map<String, Object> repoDetails = new HashMap<>();
            repoDetails.put("enabled", repo.isEnabled());
            repoDetails.put("lastScanned", repo.getLastScanned());
            repoDetails.put("snapshotCount", scanService.getSnapshotCount(repo.getId()));

            scanService.getLastScanResult(repo.getId()).ifPresent(result -> {
                repoDetails.put("lastScanStatus", result.getStatus().name());
                repoDetails.put("lastScanTime", result.getScannedAt());

                if (result.getRetentionPolicyFulfilled() != null) {
                    repoDetails.put("retentionPolicyFulfilled", result.getRetentionPolicyFulfilled());
                    if (result.getRetentionPolicyViolations() != null && !result.getRetentionPolicyViolations().isBlank()) {
                        repoDetails.put("retentionPolicyViolations", result.getRetentionPolicyViolations().split("\n"));
                    }
                }
            });

            repoDetails.put("checkEnabled", repo.getCheckIntervalMinutes() != null && repo.getCheckIntervalMinutes() > 0);
            repoDetails.put("lastChecked", repo.getLastChecked());
            checkService.getLastCheckResult(repo.getId()).ifPresent(result -> {
                repoDetails.put("lastCheckStatus", result.getStatus().name());
                repoDetails.put("lastCheckTime", result.getCheckedAt());
            });

            details.put("repository_" + repo.getName(), repoDetails);

            boolean repoFailed = false;
            var lastResult = scanService.getLastScanResult(repo.getId());
            if (lastResult.isPresent() && lastResult.get().getStatus() == ScanResult.ScanStatus.FAILED) {
                repoFailed = true;
            }

            var lastCheckResult = checkService.getLastCheckResult(repo.getId());
            if (lastCheckResult.isPresent() && lastCheckResult.get().getStatus() == CheckResult.CheckStatus.FAILED) {
                repoFailed = true;
            }

            if (repoFailed) {
                failedRepos++;
            } else if (lastResult.isPresent()) {
                successRepos++;
            }
        }

        details.put("healthyRepositories", successRepos);
        details.put("unhealthyRepositories", failedRepos);

        if (repos.isEmpty()) {
            return Health.unknown().withDetails(details).build();
        }
        if (failedRepos > 0) {
            return Health.down().withDetails(details).build();
        }
        return Health.up().withDetails(details).build();
    }
}
