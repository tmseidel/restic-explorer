package org.remus.resticexplorer.scanning;

import org.junit.jupiter.api.Test;
import org.remus.resticexplorer.repository.RepositoryService;
import org.remus.resticexplorer.repository.data.RepositoryType;
import org.remus.resticexplorer.repository.data.ResticRepository;
import org.remus.resticexplorer.scanning.data.Snapshot;
import org.remus.resticexplorer.scanning.data.SnapshotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ScanServiceTest {

    @Autowired
    private ScanService scanService;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private SnapshotRepository snapshotRepository;

    @Test
    void testGetSnapshotsEmptyRepo() {
        ResticRepository repo = createTestRepo("Test Repo");
        ResticRepository saved = repositoryService.save(repo);

        List<Snapshot> snapshots = scanService.getSnapshots(saved.getId());
        assertTrue(snapshots.isEmpty());
    }

    @Test
    void testGetSnapshotCount() {
        ResticRepository repo = createTestRepo("Count Repo");
        ResticRepository saved = repositoryService.save(repo);

        assertEquals(0, scanService.getSnapshotCount(saved.getId()));

        Snapshot snapshot = new Snapshot();
        snapshot.setRepositoryId(saved.getId());
        snapshot.setSnapshotId("abc123");
        snapshot.setHostname("test-host");
        snapshot.setSnapshotTime(LocalDateTime.now());
        snapshotRepository.save(snapshot);

        assertEquals(1, scanService.getSnapshotCount(saved.getId()));
    }

    @Test
    void testGetTotalSnapshotCount() {
        assertEquals(0, scanService.getTotalSnapshotCount());
    }

    private ResticRepository createTestRepo(String name) {
        ResticRepository repo = new ResticRepository();
        repo.setName(name);
        repo.setType(RepositoryType.S3);
        repo.setUrl("s3:https://s3.amazonaws.com/test-bucket/restic");
        repo.setRepositoryPassword("secret");
        repo.setScanIntervalMinutes(60);
        repo.setEnabled(true);
        return repo;
    }
}
