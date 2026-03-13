package org.remus.resticexplorer.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SetupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminService adminService;

    @Test
    void testShowSetup() throws Exception {
        mockMvc.perform(get("/setup"))
                .andExpect(status().isOk())
                .andExpect(view().name("setup/index"));
    }

    @Test
    void testSetupRedirectsWhenComplete() throws Exception {
        adminService.createAdmin("testpassword123");
        mockMvc.perform(get("/setup"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void testProcessSetup() throws Exception {
        mockMvc.perform(post("/setup")
                        .with(csrf())
                        .param("password", "testpassword123")
                        .param("confirmPassword", "testpassword123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?setup=true"));
    }

    @Test
    void testProcessSetupPasswordMismatch() throws Exception {
        mockMvc.perform(post("/setup")
                        .with(csrf())
                        .param("password", "testpassword123")
                        .param("confirmPassword", "differentpassword"))
                .andExpect(status().isOk())
                .andExpect(view().name("setup/index"));
    }

    @Test
    void testProcessSetupTooShortPassword() throws Exception {
        mockMvc.perform(post("/setup")
                        .with(csrf())
                        .param("password", "short")
                        .param("confirmPassword", "short"))
                .andExpect(status().isOk())
                .andExpect(view().name("setup/index"));
    }
}
