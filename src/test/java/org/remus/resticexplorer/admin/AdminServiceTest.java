package org.remus.resticexplorer.admin;

import org.junit.jupiter.api.Test;
import org.remus.resticexplorer.admin.data.AdminUser;
import org.remus.resticexplorer.admin.data.AdminUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminServiceTest {

    @Autowired
    private AdminService adminService;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Test
    void testSetupNotComplete() {
        assertFalse(adminService.isSetupComplete());
    }

    @Test
    void testCreateAdmin() {
        adminService.createAdmin("testpassword123");
        assertTrue(adminService.isSetupComplete());

        Optional<AdminUser> admin = adminUserRepository.findByUsername("admin");
        assertTrue(admin.isPresent());
        assertEquals("admin", admin.get().getUsername());
        assertNotEquals("testpassword123", admin.get().getPassword()); // Should be encoded
    }

    @Test
    void testCreateAdminTwiceFails() {
        adminService.createAdmin("testpassword123");
        assertThrows(IllegalStateException.class, () -> adminService.createAdmin("anotherpassword"));
    }

    @Test
    void testLoadUserByUsername() {
        adminService.createAdmin("testpassword123");
        Optional<UserDetails> user = adminService.loadUserByUsername("admin");
        assertTrue(user.isPresent());
        assertEquals("admin", user.get().getUsername());
        assertTrue(user.get().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void testLoadUserByUsernameNotFound() {
        Optional<UserDetails> user = adminService.loadUserByUsername("nonexistent");
        assertFalse(user.isPresent());
    }

    @Test
    void testChangePassword() {
        adminService.createAdmin("testpassword123");
        String oldPassword = adminUserRepository.findByUsername("admin").get().getPassword();

        adminService.changePassword("newpassword456");
        String newPassword = adminUserRepository.findByUsername("admin").get().getPassword();

        assertNotEquals(oldPassword, newPassword);
    }
}
