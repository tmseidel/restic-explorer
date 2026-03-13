package org.remus.resticexplorer.scanning;

import org.junit.jupiter.api.Test;
import org.remus.resticexplorer.repository.RepositoryService;
import org.remus.resticexplorer.repository.data.RepositoryType;
import org.remus.resticexplorer.repository.data.ResticRepository;
import org.remus.resticexplorer.scanning.data.Snapshot;
import org.remus.resticexplorer.scanning.data.SnapshotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    @Test
    void testGetSnapshotsWithPaginationReturnsCorrectPage() {
        ResticRepository repo = createTestRepo("Paginated Repo");
        ResticRepository saved = repositoryService.save(repo);

        LocalDateTime base = LocalDateTime.of(2024, 1, 1, 12, 0);
        for (int i = 0; i < 3; i++) {
            Snapshot snapshot = new Snapshot();
            snapshot.setRepositoryId(saved.getId());
            snapshot.setSnapshotId("snap" + i);
            snapshot.setHostname("host");
            snapshot.setSnapshotTime(base.plusDays(i));
            snapshotRepository.save(snapshot);
        }

        Page<Snapshot> page = scanService.getSnapshots(saved.getId(), PageRequest.of(0, 2));

        assertEquals(3, page.getTotalElements());
        assertEquals(2, page.getContent().size());
        assertEquals(2, page.getTotalPages());
    }

    @Test
    void testGetSnapshotsWithPaginationSortsBySnapshotTimeDesc() {
        ResticRepository repo = createTestRepo("Sorted Repo");
        ResticRepository saved = repositoryService.save(repo);

        LocalDateTime base = LocalDateTime.of(2024, 6, 1, 0, 0);
        snapshotRepository.save(createSnapshot(saved.getId(), "old", base));
        snapshotRepository.save(createSnapshot(saved.getId(), "new", base.plusDays(1)));

        Page<Snapshot> page = scanService.getSnapshots(
                saved.getId(),
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "snapshotTime")));

        List<Snapshot> content = page.getContent();
        assertEquals(2, content.size());
        assertTrue(content.get(0).getSnapshotTime().isAfter(content.get(1).getSnapshotTime()));
    }

    @Test
    void testGetSnapshotFound() {
        ResticRepository repo = createTestRepo("Lookup Repo");
        ResticRepository saved = repositoryService.save(repo);

        Snapshot snapshot = createSnapshot(saved.getId(), "abc123", LocalDateTime.now());
        snapshotRepository.save(snapshot);

        Optional<Snapshot> result = scanService.getSnapshot(saved.getId(), "abc123");

        assertTrue(result.isPresent());
        assertEquals("abc123", result.get().getSnapshotId());
        assertEquals(saved.getId(), result.get().getRepositoryId());
    }

    @Test
    void testGetSnapshotNotFound() {
        ResticRepository repo = createTestRepo("Missing Repo");
        ResticRepository saved = repositoryService.save(repo);

        Optional<Snapshot> result = scanService.getSnapshot(saved.getId(), "nonexistent");

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetSnapshotDoesNotReturnSnapshotFromDifferentRepo() {
        ResticRepository repo1 = repositoryService.save(createTestRepo("Repo One"));
        ResticRepository repo2 = repositoryService.save(createTestRepo("Repo Two"));

        snapshotRepository.save(createSnapshot(repo1.getId(), "shared-id", LocalDateTime.now()));

        Optional<Snapshot> result = scanService.getSnapshot(repo2.getId(), "shared-id");

        assertTrue(result.isEmpty());
    }

    private Snapshot createSnapshot(Long repositoryId, String snapshotId, LocalDateTime time) {
        Snapshot snapshot = new Snapshot();
        snapshot.setRepositoryId(repositoryId);
        snapshot.setSnapshotId(snapshotId);
        snapshot.setHostname("host");
        snapshot.setSnapshotTime(time);
        return snapshot;
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
