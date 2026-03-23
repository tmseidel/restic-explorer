package org.remus.resticexplorer.scanning;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.remus.resticexplorer.admin.AdminService;
import org.remus.resticexplorer.repository.RepositoryService;
import org.remus.resticexplorer.repository.data.RepositoryType;
import org.remus.resticexplorer.repository.data.ResticRepository;
import org.remus.resticexplorer.scanning.data.ScanResult;
import org.remus.resticexplorer.scanning.data.ScanResultRepository;
import org.remus.resticexplorer.scanning.data.Snapshot;
import org.remus.resticexplorer.scanning.data.SnapshotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SnapshotsPageStatsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private ScanResultRepository scanResultRepository;

    @Autowired
    private SnapshotRepository snapshotRepository;

    @Autowired
    private AdminService adminService;

    @BeforeEach
    void setUp() {
        if (!adminService.isSetupComplete()) {
            adminService.createAdmin("testpassword123");
        }
    }

    private ResticRepository createAndSaveRepo(String name) {
        ResticRepository repo = new ResticRepository();
        repo.setName(name);
        repo.setType(RepositoryType.S3);
        repo.setUrl("s3:https://s3.amazonaws.com/" + name.toLowerCase().replace(" ", "-"));
        repo.setRepositoryPassword("secret");
        repo.setScanIntervalMinutes(60);
        repo.setEnabled(true);
        return repositoryService.save(repo);
    }

    @Test
    void snapshotsPageShowsRepoStatistics() throws Exception {
        ResticRepository repo = createAndSaveRepo("Stats Repo");

        ScanResult scanResult = new ScanResult();
        scanResult.setRepositoryId(repo.getId());
        scanResult.setStatus(ScanResult.ScanStatus.SUCCESS);
        scanResult.setSnapshotCount(5);
        scanResult.setTotalSize(10485760L); // 10 MB
        scanResult.setTotalFileCount(42L);
        scanResultRepository.save(scanResult);

        mockMvc.perform(get("/repositories/" + repo.getId() + "/snapshots"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Total Size:")))
                .andExpect(content().string(containsString("Total Files:")))
                .andExpect(content().string(containsString("Snapshots:")));
    }

    @Test
    void snapshotsPageHidesStatsWhenNoScanResult() throws Exception {
        ResticRepository repo = createAndSaveRepo("No Stats Repo");

        mockMvc.perform(get("/repositories/" + repo.getId() + "/snapshots"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Total Size:"))))
                .andExpect(content().string(not(containsString("Total Files:"))));
    }

    @Test
    void snapshotsPageShowsSizeColumnInTable() throws Exception {
        ResticRepository repo = createAndSaveRepo("Size Column Repo");

        Snapshot snapshot = new Snapshot();
        snapshot.setRepositoryId(repo.getId());
        snapshot.setSnapshotId("abc123");
        snapshot.setHostname("test-host");
        snapshot.setSnapshotTime(LocalDateTime.now());
        snapshot.setTotalSize(5242880L); // 5 MB
        snapshotRepository.save(snapshot);

        mockMvc.perform(get("/repositories/" + repo.getId() + "/snapshots"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("5")))
                .andExpect(content().string(containsString("MB")));
    }

    @Test
    void snapshotsPageShowsDashWhenSnapshotHasNoSize() throws Exception {
        ResticRepository repo = createAndSaveRepo("No Size Repo");

        Snapshot snapshot = new Snapshot();
        snapshot.setRepositoryId(repo.getId());
        snapshot.setSnapshotId("def456");
        snapshot.setHostname("test-host");
        snapshot.setSnapshotTime(LocalDateTime.now());
        snapshotRepository.save(snapshot);

        mockMvc.perform(get("/repositories/" + repo.getId() + "/snapshots"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\u2014"))); // em-dash fallback
    }

    @Test
    void snapshotsPageShowsGBForLargeSnapshots() throws Exception {
        ResticRepository repo = createAndSaveRepo("GB Size Repo");

        Snapshot snapshot = new Snapshot();
        snapshot.setRepositoryId(repo.getId());
        snapshot.setSnapshotId("gb1234");
        snapshot.setHostname("test-host");
        snapshot.setSnapshotTime(LocalDateTime.now());
        snapshot.setTotalSize(5368709120L); // 5 GB
        snapshotRepository.save(snapshot);

        mockMvc.perform(get("/repositories/" + repo.getId() + "/snapshots"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("5")))
                .andExpect(content().string(containsString("GB")));
    }

    @Test
    void snapshotsPageShowsTBForVeryLargeSnapshots() throws Exception {
        ResticRepository repo = createAndSaveRepo("TB Size Repo");

        Snapshot snapshot = new Snapshot();
        snapshot.setRepositoryId(repo.getId());
        snapshot.setSnapshotId("tb5678");
        snapshot.setHostname("test-host");
        snapshot.setSnapshotTime(LocalDateTime.now());
        snapshot.setTotalSize(2199023255552L); // 2 TB
        snapshotRepository.save(snapshot);

        mockMvc.perform(get("/repositories/" + repo.getId() + "/snapshots"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2")))
                .andExpect(content().string(containsString("TB")));
    }
}
