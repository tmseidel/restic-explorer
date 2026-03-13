package org.remus.resticexplorer.scanning;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.remus.resticexplorer.config.exception.RepositoryNotFoundException;
import org.remus.resticexplorer.repository.RepositoryService;
import org.remus.resticexplorer.repository.data.ResticRepository;
import org.remus.resticexplorer.restic.ResticCommandService;
import org.remus.resticexplorer.scanning.data.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScanService {

    private final RepositoryService repositoryService;
    private final SnapshotRepository snapshotRepository;
    private final ScanResultRepository scanResultRepository;
    private final ResticCommandService resticCommandService;

    @Scheduled(fixedDelayString = "${restic.scan.check-interval:60000}")
    public void scheduledScan() {
        List<ResticRepository> repos = repositoryService.findAllEnabled();
        for (ResticRepository repo : repos) {
            if (shouldScan(repo)) {
                scanRepository(repo.getId());
            }
        }
    }

    private boolean shouldScan(ResticRepository repo) {
        if (repo.getLastScanned() == null) {
            return true;
        }
        LocalDateTime nextScan = repo.getLastScanned().plusMinutes(repo.getScanIntervalMinutes());
        return LocalDateTime.now().isAfter(nextScan);
    }

    @Transactional
    public void scanRepository(Long repositoryId) {
        ResticRepository repo = repositoryService.findById(repositoryId)
                .orElseThrow(() -> new RepositoryNotFoundException(repositoryId));

        ScanResult scanResult = new ScanResult();
        scanResult.setRepositoryId(repositoryId);
        scanResult.setStatus(ScanResult.ScanStatus.IN_PROGRESS);
        scanResultRepository.save(scanResult);

        try {
            List<Map<String, Object>> snapshots = resticCommandService.listSnapshots(repo);
            snapshotRepository.deleteByRepositoryId(repositoryId);

            long totalSize = 0;
            for (Map<String, Object> snapshotData : snapshots) {
                Snapshot snapshot = new Snapshot();
                snapshot.setRepositoryId(repositoryId);
                snapshot.setSnapshotId((String) snapshotData.get("short_id"));
                snapshot.setHostname((String) snapshotData.get("hostname"));
                snapshot.setUsername((String) snapshotData.get("username"));

                Object pathsObj = snapshotData.get("paths");
                if (pathsObj instanceof List) {
                    snapshot.setPaths(String.join(", ", (List<String>) pathsObj));
                }

                Object tagsObj = snapshotData.get("tags");
                if (tagsObj instanceof List) {
                    snapshot.setTags(String.join(", ", (List<String>) tagsObj));
                }

                String timeStr = (String) snapshotData.get("time");
                if (timeStr != null) {
                    snapshot.setSnapshotTime(parseResticTime(timeStr));
                }

                snapshot.setTreeHash((String) snapshotData.get("tree"));
                snapshotRepository.save(snapshot);
            }

            Map<String, Object> stats = resticCommandService.getStats(repo);
            if (stats.containsKey("total_size")) {
                totalSize = ((Number) stats.get("total_size")).longValue();
            }
            long totalFiles = 0;
            if (stats.containsKey("total_file_count")) {
                totalFiles = ((Number) stats.get("total_file_count")).longValue();
            }

            scanResult.setStatus(ScanResult.ScanStatus.SUCCESS);
            scanResult.setSnapshotCount(snapshots.size());
            scanResult.setTotalSize(totalSize);
            scanResultRepository.save(scanResult);

            repo.setLastScanned(LocalDateTime.now());
            repositoryService.save(repo);

            log.info("Successfully scanned repository '{}': {} snapshots found", repo.getName(), snapshots.size());
        } catch (Exception e) {
            log.error("Failed to scan repository '{}': {}", repo.getName(), e.getMessage(), e);
            scanResult.setStatus(ScanResult.ScanStatus.FAILED);
            scanResult.setMessage(e.getMessage());
            scanResultRepository.save(scanResult);
        }
    }

    public List<Snapshot> getSnapshots(Long repositoryId) {
        return snapshotRepository.findByRepositoryIdOrderBySnapshotTimeDesc(repositoryId);
    }

    public Optional<ScanResult> getLastScanResult(Long repositoryId) {
        return scanResultRepository.findTopByRepositoryIdOrderByScannedAtDesc(repositoryId);
    }

    public long getTotalSnapshotCount() {
        return snapshotRepository.count();
    }

    public long getSnapshotCount(Long repositoryId) {
        return snapshotRepository.countByRepositoryId(repositoryId);
    }

    public List<ScanResult> getAllLatestScanResults() {
        return scanResultRepository.findAll();
    }

    private LocalDateTime parseResticTime(String timeStr) {
        try {
            if (timeStr.length() > 19) {
                timeStr = timeStr.substring(0, 19);
            }
            return LocalDateTime.parse(timeStr);
        } catch (Exception e) {
            log.warn("Could not parse time: {}", timeStr);
            return null;
        }
    }
}
