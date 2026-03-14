package org.remus.resticexplorer.scanning;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.remus.resticexplorer.config.exception.RepositoryNotFoundException;
import org.remus.resticexplorer.repository.RepositoryService;
import org.remus.resticexplorer.repository.data.ResticRepository;
import org.remus.resticexplorer.restic.ResticCommandService;
import org.remus.resticexplorer.scanning.data.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final CheckResultRepository checkResultRepository;
    private final ResticCommandService resticCommandService;

    @Scheduled(fixedDelayString = "${restic.scan.check-interval:60000}")
    public void scheduledScan() {
        List<ResticRepository> repos = repositoryService.findAllEnabled();
        for (ResticRepository repo : repos) {
            if (shouldScan(repo)) {
                scanRepository(repo.getId());
            }
            if (shouldCheck(repo)) {
                checkRepository(repo.getId());
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

    private boolean shouldCheck(ResticRepository repo) {
        if (repo.getCheckIntervalMinutes() == null || repo.getCheckIntervalMinutes() <= 0) {
            return false;
        }
        if (repo.getLastChecked() == null) {
            return true;
        }
        LocalDateTime nextCheck = repo.getLastChecked().plusMinutes(repo.getCheckIntervalMinutes());
        return LocalDateTime.now().isAfter(nextCheck);
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

    @Transactional
    public void checkRepository(Long repositoryId) {
        ResticRepository repo = repositoryService.findById(repositoryId)
                .orElseThrow(() -> new RepositoryNotFoundException(repositoryId));

        CheckResult checkResult = new CheckResult();
        checkResult.setRepositoryId(repositoryId);
        checkResult.setStatus(CheckResult.CheckStatus.IN_PROGRESS);
        checkResultRepository.save(checkResult);

        try {
            String output = resticCommandService.checkRepository(repo);

            checkResult.setStatus(CheckResult.CheckStatus.SUCCESS);
            checkResult.setMessage(output);
            checkResultRepository.save(checkResult);

            repo.setLastChecked(LocalDateTime.now());
            repositoryService.save(repo);

            log.info("Integrity check passed for repository '{}'", repo.getName());
        } catch (Exception e) {
            log.error("Integrity check failed for repository '{}': {}", repo.getName(), e.getMessage(), e);
            checkResult.setStatus(CheckResult.CheckStatus.FAILED);
            checkResult.setMessage(e.getMessage());
            checkResultRepository.save(checkResult);
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

    public Optional<CheckResult> getLastCheckResult(Long repositoryId) {
        return checkResultRepository.findTopByRepositoryIdOrderByCheckedAtDesc(repositoryId);
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
