package org.remus.resticexplorer.scanning;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.remus.resticexplorer.admin.ErrorLogService;
import org.remus.resticexplorer.config.exception.RepositoryNotFoundException;
import org.remus.resticexplorer.repository.RepositoryService;
import org.remus.resticexplorer.repository.data.RepositoryType;
import org.remus.resticexplorer.repository.data.ResticRepository;
import org.remus.resticexplorer.restic.ResticCommandService;
import org.remus.resticexplorer.scanning.data.ScanResult;
import org.remus.resticexplorer.scanning.data.ScanResult.ScanStatus;
import org.remus.resticexplorer.scanning.data.ScanResultRepository;
import org.remus.resticexplorer.scanning.data.Snapshot;
import org.remus.resticexplorer.scanning.data.SnapshotRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ScanService#scanRepository(Long)} focused on the repository-size counting
 * modes. These verify that the on-disk size is taken from the {@code raw-data} stats call (not the
 * logical {@code restore-size} default) while the file count is taken from the {@code restore-size}
 * call, and that per-snapshot sizes use {@code restore-size}.
 *
 * <p>All collaborators are mocked, so no real restic CLI or Spring context is required.
 */
@ExtendWith(MockitoExtension.class)
class ScanServiceScanRepositoryTest {

    @Mock
    private RepositoryService repositoryService;
    @Mock
    private SnapshotRepository snapshotRepository;
    @Mock
    private ScanResultRepository scanResultRepository;
    @Mock
    private ResticCommandService resticCommandService;
    @Mock
    private RetentionPolicyChecker retentionPolicyChecker;
    @Mock
    private ErrorLogService errorLogService;

    private ScanService scanService;

    @BeforeEach
    void setUp() {
        // Field order matches ScanService (Lombok @RequiredArgsConstructor).
        scanService = new ScanService(
                repositoryService,
                snapshotRepository,
                scanResultRepository,
                resticCommandService,
                retentionPolicyChecker,
                errorLogService);
    }

    @Test
    void repositorySizeIsOnDiskAndFileCountIsLogical() {
        long repoId = 1L;
        ResticRepository repo = s3Repo(repoId);
        when(repositoryService.findById(repoId)).thenReturn(Optional.of(repo));
        when(snapshotRepository.findByRepositoryIdOrderBySnapshotTimeDesc(repoId)).thenReturn(List.of());
        when(resticCommandService.listSnapshots(repo)).thenReturn(List.of());
        when(resticCommandService.listLocks(repo)).thenReturn(List.of());
        when(retentionPolicyChecker.check(eq(repo), any(), any()))
                .thenReturn(RetentionPolicyResult.ok());
        // raw-data (on disk): small size, blob count only, no file count.
        when(resticCommandService.getStats(repo, "raw-data"))
                .thenReturn(Map.of("mode", "raw-data", "total_size", 1000L, "total_blob_count", 50L));
        // restore-size (logical): large size, with file count.
        when(resticCommandService.getStats(repo, "restore-size"))
                .thenReturn(Map.of("mode", "restore-size", "total_size", 5000L, "total_file_count", 42L));

        scanService.scanRepository(repoId);

        ArgumentCaptor<ScanResult> captor = ArgumentCaptor.forClass(ScanResult.class);
        verify(scanResultRepository, atLeastOnce()).save(captor.capture());
        ScanResult saved = captor.getValue();

        // The reported repository size must be the raw-data (on-disk) value, not the logical size.
        assertThat(saved.getTotalSize()).isEqualTo(1000L);
        // The file count must come from the restore-size call (raw-data has no file count).
        assertThat(saved.getTotalFileCount()).isEqualTo(42L);
        assertThat(saved.getStatus()).isEqualTo(ScanStatus.SUCCESS);

        // The two distinct stats calls prove the size and the count are not conflated.
        verify(resticCommandService).getStats(repo, "raw-data");
        verify(resticCommandService).getStats(repo, "restore-size");
    }

    @Test
    void perSnapshotSizeUsesRestoreSizeMode() {
        long repoId = 2L;
        ResticRepository repo = s3Repo(repoId);
        when(repositoryService.findById(repoId)).thenReturn(Optional.of(repo));
        when(snapshotRepository.findByRepositoryIdOrderBySnapshotTimeDesc(repoId)).thenReturn(List.of());
        when(resticCommandService.listLocks(repo)).thenReturn(List.of());
        when(retentionPolicyChecker.check(eq(repo), any(), any()))
                .thenReturn(RetentionPolicyResult.ok());
        when(resticCommandService.getStats(repo, "raw-data"))
                .thenReturn(Map.of("mode", "raw-data", "total_size", 1000L, "total_blob_count", 50L));
        when(resticCommandService.getStats(repo, "restore-size"))
                .thenReturn(Map.of("mode", "restore-size", "total_size", 5000L, "total_file_count", 42L));
        when(resticCommandService.listSnapshots(repo))
                .thenReturn(List.of(Map.of("short_id", "abc123", "hostname", "host", "time",
                        "2024-01-01T12:00:00")));
        when(resticCommandService.getSnapshotStats(repo, "abc123", "restore-size"))
                .thenReturn(Map.of("mode", "restore-size", "total_size", 2500L, "total_file_count", 9L));

        scanService.scanRepository(repoId);

        ArgumentCaptor<Snapshot> snapCaptor = ArgumentCaptor.forClass(Snapshot.class);
        verify(snapshotRepository, atLeastOnce()).save(snapCaptor.capture());
        Snapshot saved = snapCaptor.getValue();

        // The snapshot's own size must be its logical (restore-size) value.
        assertThat(saved.getTotalSize()).isEqualTo(2500L);
        assertThat(saved.getTotalFileCount()).isEqualTo(9L);
        assertThat(saved.getSnapshotId()).isEqualTo("abc123");
        verify(resticCommandService).getSnapshotStats(repo, "abc123", "restore-size");
    }

    @Test
    void missingRepository_throwsWithoutPersistingOrLogging() {
        long missingId = 99L;
        when(repositoryService.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scanService.scanRepository(missingId))
                .isInstanceOf(RepositoryNotFoundException.class)
                .hasMessageContaining(String.valueOf(missingId));

        // The lookup happens before any ScanResult is created, so there is nothing to persist
        // and no restic command is issued for a missing repository.
        verify(scanResultRepository, never()).save(any());
        verify(errorLogService, never()).logError(anyLong(), anyString(), anyString(), anyString(), any());
        verify(resticCommandService, never()).getStats(any(), anyString());
    }

    private ResticRepository s3Repo(Long id) {
        ResticRepository repo = new ResticRepository();
        repo.setId(id);
        repo.setName("Test Repo");
        repo.setType(RepositoryType.S3);
        repo.setUrl("s3:https://s3.amazonaws.com/test-bucket/restic");
        repo.setRepositoryPassword("secret");
        repo.setScanIntervalMinutes(60);
        repo.setEnabled(true);
        repo.setLastScanned(LocalDateTime.now().minusMinutes(120));
        return repo;
    }
}
