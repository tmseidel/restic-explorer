package org.remus.resticexplorer.admin;

import org.junit.jupiter.api.Test;
import org.remus.resticexplorer.config.OAuth2TestConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "restic.auth.mode=oauth2")
@Import(OAuth2TestConfig.class)
@Transactional
class AdminServiceOAuth2Test {

    @Autowired
    private AdminService adminService;

    @Test
    void testOAuth2ModeIsEnabled() {
        assertTrue(adminService.isOAuth2AuthEnabled());
        assertFalse(adminService.isLocalAuthEnabled());
    }

    @Test
    void testCreateAdminThrowsInOAuth2Mode() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> adminService.createAdmin("testpassword123"));
        assertTrue(exception.getMessage().contains("OAuth2"));
    }

    @Test
    void testChangePasswordThrowsInOAuth2Mode() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> adminService.changePassword("newpassword456"));
        assertTrue(exception.getMessage().contains("OAuth2"));
    }
}
