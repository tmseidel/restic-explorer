package org.remus.resticexplorer.health;

import lombok.RequiredArgsConstructor;
import org.remus.resticexplorer.repository.RepositoryService;
import org.remus.resticexplorer.repository.data.ResticRepository;
import org.remus.resticexplorer.scanning.ScanService;
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
            });

            details.put("repository_" + repo.getName(), repoDetails);

            var lastResult = scanService.getLastScanResult(repo.getId());
            if (lastResult.isPresent() && lastResult.get().getStatus() == ScanResult.ScanStatus.FAILED) {
                failedRepos++;
            } else if (lastResult.isPresent()) {
                successRepos++;
            }
        }

        details.put("successfulScans", successRepos);
        details.put("failedScans", failedRepos);

        if (repos.isEmpty()) {
            return Health.unknown().withDetails(details).build();
        }
        if (failedRepos > 0) {
            return Health.down().withDetails(details).build();
        }
        return Health.up().withDetails(details).build();
    }
}
