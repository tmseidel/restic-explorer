package org.remus.resticexplorer.health;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HealthPolicyStatusTest {

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
    void healthIncludesRetentionPolicyFulfilled() throws Exception {
        ResticRepository repo = createAndSaveRepo("Policy OK Repo");
        ScanResult result = new ScanResult();
        result.setRepositoryId(repo.getId());
        result.setStatus(ScanResult.ScanStatus.SUCCESS);
        result.setRetentionPolicyFulfilled(true);
        scanResultRepository.save(result);

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.resticMetadata.details.['repository_Policy OK Repo'].retentionPolicyFulfilled").value(true));
    }

    @Test
    void healthIncludesRetentionPolicyViolations() throws Exception {
        ResticRepository repo = createAndSaveRepo("Policy Warn Repo");
        ScanResult result = new ScanResult();
        result.setRepositoryId(repo.getId());
        result.setStatus(ScanResult.ScanStatus.SUCCESS);
        result.setRetentionPolicyFulfilled(false);
        result.setRetentionPolicyViolations("keepDaily: Missing backup for 2026-03-12\nkeepWeekly: No backup in week 2026-W10");
        scanResultRepository.save(result);

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.resticMetadata.details.['repository_Policy Warn Repo'].retentionPolicyFulfilled").value(false))
                .andExpect(jsonPath("$.components.resticMetadata.details.['repository_Policy Warn Repo'].retentionPolicyViolations[0]").value("keepDaily: Missing backup for 2026-03-12"))
                .andExpect(jsonPath("$.components.resticMetadata.details.['repository_Policy Warn Repo'].retentionPolicyViolations[1]").value("keepWeekly: No backup in week 2026-W10"));
    }

    @Test
    void healthOmitsPolicyFieldsWhenNoPolicyConfigured() throws Exception {
        ResticRepository repo = createAndSaveRepo("No Policy Repo");
        ScanResult result = new ScanResult();
        result.setRepositoryId(repo.getId());
        result.setStatus(ScanResult.ScanStatus.SUCCESS);
        scanResultRepository.save(result);

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.resticMetadata.details.['repository_No Policy Repo'].retentionPolicyFulfilled").doesNotExist())
                .andExpect(jsonPath("$.components.resticMetadata.details.['repository_No Policy Repo'].retentionPolicyViolations").doesNotExist());
    }
}
