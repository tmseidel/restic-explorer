package org.remus.resticexplorer.scanning;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.remus.resticexplorer.admin.ErrorLogService;
import org.remus.resticexplorer.config.exception.RepositoryNotFoundException;
import org.remus.resticexplorer.repository.RepositoryService;
import org.remus.resticexplorer.repository.data.ResticRepository;
import org.remus.resticexplorer.restic.ResticCommandService;
import org.remus.resticexplorer.scanning.data.ScanResult;
import org.remus.resticexplorer.scanning.data.ScanResultRepository;
import org.remus.resticexplorer.scanning.data.Snapshot;
import org.remus.resticexplorer.scanning.data.SnapshotRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
    private final RetentionPolicyChecker retentionPolicyChecker;
    private final ErrorLogService errorLogService;

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

            scanResult.setStatus(ScanResult.ScanStatus.SUCCESS);
            scanResult.setSnapshotCount(snapshots.size());
            scanResult.setTotalSize(totalSize);

            // Check for repository locks
            try {
                List<String> locks = resticCommandService.listLocks(repo);
                scanResult.setLockCount(locks.size());
            } catch (Exception e) {
                log.warn("Failed to list locks for repository '{}': {}", repo.getName(), e.getMessage());
                scanResult.setLockCount(null);
            }

            // Check retention policy against the freshly saved snapshots
            List<Snapshot> savedSnapshots = snapshotRepository.findByRepositoryIdOrderBySnapshotTimeDesc(repositoryId);
            RetentionPolicyResult policyResult = retentionPolicyChecker.check(repo, savedSnapshots, LocalDate.now());
            scanResult.setRetentionPolicyFulfilled(policyResult.isFulfilled());
            if (!policyResult.getViolations().isEmpty()) {
                scanResult.setRetentionPolicyViolations(String.join("\n", policyResult.getViolations()));
            }

            scanResultRepository.save(scanResult);

            repo.setLastScanned(LocalDateTime.now());
            repositoryService.save(repo);

            log.info("Successfully scanned repository '{}': {} snapshots found", repo.getName(), snapshots.size());
        } catch (Exception e) {
            log.error("Failed to scan repository '{}': {}", repo.getName(), e.getMessage(), e);
            scanResult.setStatus(ScanResult.ScanStatus.FAILED);
            scanResult.setMessage(e.getMessage());
            scanResultRepository.save(scanResult);
            errorLogService.logError(repositoryId, repo.getName(), "SCAN", e.getMessage(), e);
        }
    }

    public List<Snapshot> getSnapshots(Long repositoryId) {
        return snapshotRepository.findByRepositoryIdOrderBySnapshotTimeDesc(repositoryId);
    }

    public Page<Snapshot> getSnapshots(Long repositoryId, Pageable pageable) {
        return snapshotRepository.findByRepositoryId(repositoryId, pageable);
    }

    public Optional<Snapshot> getSnapshot(Long repositoryId, String snapshotId) {
        return snapshotRepository.findByRepositoryIdAndSnapshotId(repositoryId, snapshotId);
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
