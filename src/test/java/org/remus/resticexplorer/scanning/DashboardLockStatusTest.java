package org.remus.resticexplorer.scanning;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.remus.resticexplorer.admin.AdminService;
import org.remus.resticexplorer.repository.RepositoryService;
import org.remus.resticexplorer.repository.data.RepositoryType;
import org.remus.resticexplorer.repository.data.ResticRepository;
import org.remus.resticexplorer.scanning.data.ScanResult;
import org.remus.resticexplorer.scanning.data.ScanResultRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DashboardLockStatusTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private ScanResultRepository scanResultRepository;

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
    void lockCountIsPersistedOnScanResult() {
        ResticRepository repo = createAndSaveRepo("Lock Test Repo");

        ScanResult result = new ScanResult();
        result.setRepositoryId(repo.getId());
        result.setStatus(ScanResult.ScanStatus.SUCCESS);
        result.setLockCount(3);
        scanResultRepository.save(result);

        ScanResult loaded = scanResultRepository.findTopByRepositoryIdOrderByScannedAtDesc(repo.getId())
                .orElse(null);
        assertNotNull(loaded);
        assertEquals(3, loaded.getLockCount());
    }

    @Test
    void lockCountNullWhenNotSet() {
        ResticRepository repo = createAndSaveRepo("No Lock Repo");

        ScanResult result = new ScanResult();
        result.setRepositoryId(repo.getId());
        result.setStatus(ScanResult.ScanStatus.SUCCESS);
        scanResultRepository.save(result);

        ScanResult loaded = scanResultRepository.findTopByRepositoryIdOrderByScannedAtDesc(repo.getId())
                .orElse(null);
        assertNotNull(loaded);
        assertNull(loaded.getLockCount());
    }

    @Test
    void dashboardShowsLockBadgeWhenLocksPresent() throws Exception {
        ResticRepository repo = createAndSaveRepo("Locked Repo");

        ScanResult result = new ScanResult();
        result.setRepositoryId(repo.getId());
        result.setStatus(ScanResult.ScanStatus.SUCCESS);
        result.setLockCount(2);
        scanResultRepository.save(result);

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("bi-lock-fill")))
                .andExpect(model().attributeExists("lockedRepoIds"));
    }

    @Test
    void dashboardDoesNotShowLockBadgeWhenNoLocks() throws Exception {
        ResticRepository repo = createAndSaveRepo("Unlocked Repo");

        ScanResult result = new ScanResult();
        result.setRepositoryId(repo.getId());
        result.setStatus(ScanResult.ScanStatus.SUCCESS);
        result.setLockCount(0);
        scanResultRepository.save(result);

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("bi-lock-fill"))));
    }

    @Test
    void snapshotsPageShowsLockInfo() throws Exception {
        ResticRepository repo = createAndSaveRepo("Snap Lock Repo");

        ScanResult result = new ScanResult();
        result.setRepositoryId(repo.getId());
        result.setStatus(ScanResult.ScanStatus.SUCCESS);
        result.setLockCount(1);
        scanResultRepository.save(result);

        mockMvc.perform(get("/repositories/" + repo.getId() + "/snapshots"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("bi-lock-fill")))
                .andExpect(model().attribute("hasLocks", true));
    }

    @Test
    void unlockEndpointRequiresAdmin() throws Exception {
        ResticRepository repo = createAndSaveRepo("Admin Lock Repo");

        mockMvc.perform(post("/repositories/" + repo.getId() + "/unlock")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void unlockEndpointAccessibleToAdmin() throws Exception {
        ResticRepository repo = createAndSaveRepo("Admin Unlock Repo");

        // The actual unlock will fail because there's no restic binary,
        // but it should redirect (not 403) when accessed as admin
        mockMvc.perform(post("/repositories/" + repo.getId() + "/unlock")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
    }
}
