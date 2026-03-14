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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DashboardCheckStatusTest {

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

    private ResticRepository createAndSaveRepo(String name, int checkInterval) {
        ResticRepository repo = new ResticRepository();
        repo.setName(name);
        repo.setType(RepositoryType.S3);
        repo.setUrl("s3:https://s3.amazonaws.com/" + name.toLowerCase().replace(" ", "-"));
        repo.setRepositoryPassword("secret");
        repo.setScanIntervalMinutes(60);
        repo.setCheckIntervalMinutes(checkInterval);
        repo.setEnabled(true);
        return repositoryService.save(repo);
    }

    @Test
    void dashboardShowsCheckResultsInModel() throws Exception {
        ResticRepository repo = createAndSaveRepo("Check Repo", 60);

        ScanResult scanResult = new ScanResult();
        scanResult.setRepositoryId(repo.getId());
        scanResult.setStatus(ScanResult.ScanStatus.SUCCESS);
        scanResultRepository.save(scanResult);

        CheckResult checkResult = new CheckResult();
        checkResult.setRepositoryId(repo.getId());
        checkResult.setStatus(CheckResult.CheckStatus.SUCCESS);
        checkResultRepository.save(checkResult);

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("lastCheckResults"));
    }

    @Test
    void snapshotsPageShowsCheckResult() throws Exception {
        ResticRepository repo = createAndSaveRepo("Snapshot Check Repo", 60);

        CheckResult checkResult = new CheckResult();
        checkResult.setRepositoryId(repo.getId());
        checkResult.setStatus(CheckResult.CheckStatus.SUCCESS);
        checkResultRepository.save(checkResult);

        mockMvc.perform(get("/repositories/" + repo.getId() + "/snapshots"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("lastCheckResult"));
    }

    @Test
    void dashboardShowsIntegrityColumnForRepoWithCheckEnabled() throws Exception {
        ResticRepository repo = createAndSaveRepo("Integrity Repo", 120);

        ScanResult scanResult = new ScanResult();
        scanResult.setRepositoryId(repo.getId());
        scanResult.setStatus(ScanResult.ScanStatus.SUCCESS);
        scanResultRepository.save(scanResult);

        CheckResult checkResult = new CheckResult();
        checkResult.setRepositoryId(repo.getId());
        checkResult.setStatus(CheckResult.CheckStatus.SUCCESS);
        checkResultRepository.save(checkResult);

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Integrity")));
    }
}
