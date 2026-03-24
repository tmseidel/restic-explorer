package org.remus.resticexplorer.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.remus.resticexplorer.admin.data.ErrorLogEntry;
import org.remus.resticexplorer.admin.data.ErrorLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminService adminService;

    @Autowired
    private BuildProperties buildProperties;

    @Autowired
    private ErrorLogRepository errorLogRepository;

    @BeforeEach
    void setUp() {
        if (!adminService.isSetupComplete()) {
            adminService.createAdmin("testpassword123");
        }
    }

    @Test
    void testAdminPanelShowsVersion() throws Exception {
        String expectedVersion = buildProperties.getVersion();

        mockMvc.perform(get("/admin")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/index"))
                .andExpect(model().attribute("appVersion", expectedVersion));
    }

    @Test
    void testAdminPanelVersionNotBlank() throws Exception {
        mockMvc.perform(get("/admin")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("appVersion", is(not(emptyOrNullString()))));
    }

    @Test
    void testAdminPanelRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void testDeleteAllErrorLogsDeletesEntries() throws Exception {
        ErrorLogEntry entry = new ErrorLogEntry();
        entry.setRepositoryId(1L);
        entry.setRepositoryName("TestRepo");
        entry.setAction("SCAN");
        entry.setErrorMessage("test error");
        entry.setTimestamp(LocalDateTime.now());
        errorLogRepository.saveAndFlush(entry);

        assertEquals(1, errorLogRepository.count());

        mockMvc.perform(post("/admin/error-log/delete-all")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/error-log"))
                .andExpect(flash().attribute("successMessage", "admin.errorLog.deleteAll.success"));

        assertEquals(0, errorLogRepository.count());
    }

    @Test
    void testDeleteAllErrorLogsRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/admin/error-log/delete-all")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
    }
}

