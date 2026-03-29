package org.remus.resticexplorer.scanning;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.remus.resticexplorer.admin.AdminService;
import org.remus.resticexplorer.repository.RepositoryService;
import org.remus.resticexplorer.repository.data.RepositoryType;
import org.remus.resticexplorer.repository.data.ResticRepository;
import org.remus.resticexplorer.scanning.data.CheckResult;
import org.remus.resticexplorer.scanning.data.CheckResultRepository;
import org.remus.resticexplorer.scanning.data.ScanResult;
import org.remus.resticexplorer.scanning.data.ScanResultRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StatusApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private ScanResultRepository scanResultRepository;

    @Autowired
    private CheckResultRepository checkResultRepository;

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

    private void createScanResult(Long repositoryId, ScanResult.ScanStatus status) {
        ScanResult result = new ScanResult();
        result.setRepositoryId(repositoryId);
        result.setStatus(status);
        if (status == ScanResult.ScanStatus.FAILED) {
            result.setMessage("Test failure");
        }
        scanResultRepository.save(result);
    }

    private void createCheckResult(Long repositoryId, CheckResult.CheckStatus status) {
        CheckResult result = new CheckResult();
        result.setRepositoryId(repositoryId);
        result.setStatus(status);
        checkResultRepository.save(result);
    }

    @Test
    void statusEndpointReturnsNoneWhenNoRepositories() throws Exception {
        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallStatus", is("none")))
                .andExpect(jsonPath("$.activeJobs", hasSize(0)));
    }

    @Test
    void statusEndpointReturnsPendingWhenNoScans() throws Exception {
        createAndSaveRepo("Pending Repo");

        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallStatus", is("pending")))
                .andExpect(jsonPath("$.activeJobs", hasSize(0)));
    }

    @Test
    void statusEndpointReturnsOkWhenAllSuccess() throws Exception {
        ResticRepository repo1 = createAndSaveRepo("OK Repo 1");
        ResticRepository repo2 = createAndSaveRepo("OK Repo 2");
        createScanResult(repo1.getId(), ScanResult.ScanStatus.SUCCESS);
        createScanResult(repo2.getId(), ScanResult.ScanStatus.SUCCESS);

        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallStatus", is("ok")))
                .andExpect(jsonPath("$.repositories." + repo1.getId() + ".scanStatus", is("SUCCESS")))
                .andExpect(jsonPath("$.repositories." + repo2.getId() + ".scanStatus", is("SUCCESS")))
                .andExpect(jsonPath("$.activeJobs", hasSize(0)));
    }

    @Test
    void statusEndpointReturnsFailedWhenAnyScanFailed() throws Exception {
        ResticRepository repo1 = createAndSaveRepo("Good Repo");
        ResticRepository repo2 = createAndSaveRepo("Bad Repo");
        createScanResult(repo1.getId(), ScanResult.ScanStatus.SUCCESS);
        createScanResult(repo2.getId(), ScanResult.ScanStatus.FAILED);

        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallStatus", is("failed")));
    }

    @Test
    void statusEndpointReturnsScanningWithActiveJobs() throws Exception {
        ResticRepository repo = createAndSaveRepo("Scanning Repo");
        createScanResult(repo.getId(), ScanResult.ScanStatus.IN_PROGRESS);

        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallStatus", is("scanning")))
                .andExpect(jsonPath("$.activeJobs", hasSize(1)))
                .andExpect(jsonPath("$.activeJobs[0].repositoryId", is(repo.getId().intValue())))
                .andExpect(jsonPath("$.activeJobs[0].repositoryName", is("Scanning Repo")))
                .andExpect(jsonPath("$.activeJobs[0].type", is("SCAN")));
    }

    @Test
    void statusEndpointTracksCheckJobs() throws Exception {
        ResticRepository repo = createAndSaveRepo("Checking Repo");
        createScanResult(repo.getId(), ScanResult.ScanStatus.SUCCESS);
        createCheckResult(repo.getId(), CheckResult.CheckStatus.IN_PROGRESS);

        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeJobs", hasSize(1)))
                .andExpect(jsonPath("$.activeJobs[0].type", is("CHECK")));
    }

    @Test
    void statusEndpointIncludesSnapshotCount() throws Exception {
        ResticRepository repo = createAndSaveRepo("Count Repo");
        createScanResult(repo.getId(), ScanResult.ScanStatus.SUCCESS);

        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repositories." + repo.getId() + ".snapshotCount", is(0)));
    }

    @Test
    void statusEndpointIncludesCheckStatus() throws Exception {
        ResticRepository repo = createAndSaveRepo("Checked Repo");
        createScanResult(repo.getId(), ScanResult.ScanStatus.SUCCESS);
        createCheckResult(repo.getId(), CheckResult.CheckStatus.SUCCESS);

        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repositories." + repo.getId() + ".checkStatus", is("SUCCESS")));
    }

    @Test
    void statusEndpointReturnsNullCheckStatusWhenNotConfigured() throws Exception {
        ResticRepository repo = createAndSaveRepo("No Check Repo");
        createScanResult(repo.getId(), ScanResult.ScanStatus.SUCCESS);

        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repositories." + repo.getId() + ".checkStatus", is(nullValue())));
    }

    @Test
    void failedTakesPrecedenceOverInProgress() throws Exception {
        ResticRepository repo1 = createAndSaveRepo("Failed Repo");
        ResticRepository repo2 = createAndSaveRepo("Scanning Repo");
        createScanResult(repo1.getId(), ScanResult.ScanStatus.FAILED);
        createScanResult(repo2.getId(), ScanResult.ScanStatus.IN_PROGRESS);

        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallStatus", is("failed")));
    }
}
