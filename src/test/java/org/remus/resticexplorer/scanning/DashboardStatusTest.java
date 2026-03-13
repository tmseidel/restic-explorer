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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DashboardStatusTest {

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
        // Ensure setup is complete so interceptor doesn't redirect
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

    private void createScanResult(Long repositoryId, ScanResult.ScanStatus status) {
        ScanResult result = new ScanResult();
        result.setRepositoryId(repositoryId);
        result.setStatus(status);
        if (status == ScanResult.ScanStatus.FAILED) {
            result.setMessage("Test failure");
        }
        scanResultRepository.save(result);
    }

    @Test
    void statusIsNoneWhenNoRepositories() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("overallStatus", "none"));
    }

    @Test
    void statusIsPendingWhenRepoHasNoScanResults() throws Exception {
        createAndSaveRepo("Pending Repo");

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("overallStatus", "pending"));
    }

    @Test
    void statusIsOkWhenAllScansSucceeded() throws Exception {
        ResticRepository repo1 = createAndSaveRepo("OK Repo 1");
        ResticRepository repo2 = createAndSaveRepo("OK Repo 2");
        createScanResult(repo1.getId(), ScanResult.ScanStatus.SUCCESS);
        createScanResult(repo2.getId(), ScanResult.ScanStatus.SUCCESS);

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("overallStatus", "ok"))
                .andExpect(content().string(containsString("bi-check-circle")));
    }

    @Test
    void statusIsFailedWhenAnyScanFailed() throws Exception {
        ResticRepository repo1 = createAndSaveRepo("Good Repo");
        ResticRepository repo2 = createAndSaveRepo("Bad Repo");
        createScanResult(repo1.getId(), ScanResult.ScanStatus.SUCCESS);
        createScanResult(repo2.getId(), ScanResult.ScanStatus.FAILED);

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("overallStatus", "failed"))
                .andExpect(content().string(containsString("bi-x-circle")));
    }

    @Test
    void statusIsScanningWhenAnyInProgress() throws Exception {
        ResticRepository repo = createAndSaveRepo("Scanning Repo");
        createScanResult(repo.getId(), ScanResult.ScanStatus.IN_PROGRESS);

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("overallStatus", "scanning"));
    }

    @Test
    void failedTakesPrecedenceOverInProgress() throws Exception {
        ResticRepository repo1 = createAndSaveRepo("Failed Repo");
        ResticRepository repo2 = createAndSaveRepo("Scanning Repo");
        createScanResult(repo1.getId(), ScanResult.ScanStatus.FAILED);
        createScanResult(repo2.getId(), ScanResult.ScanStatus.IN_PROGRESS);

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("overallStatus", "failed"));
    }
}

