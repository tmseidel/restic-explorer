package org.remus.resticexplorer.admin;

import lombok.RequiredArgsConstructor;
import org.remus.resticexplorer.admin.data.AdminUser;
import org.remus.resticexplorer.admin.data.AdminUserRepository;
import org.remus.resticexplorer.config.AuthProperties;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties authProperties;

    public boolean isLocalAuthEnabled() {
        return authProperties.getMode() == AuthProperties.AuthMode.LOCAL;
    }

    public boolean isOAuth2AuthEnabled() {
        return authProperties.getMode() == AuthProperties.AuthMode.OAUTH2;
    }

    public boolean isSetupComplete() {
        return adminUserRepository.count() > 0;
    }

    public void createAdmin(String password) {
        if (isOAuth2AuthEnabled()) {
            throw new IllegalStateException("Admin account creation is not available when OAuth2 authentication is enabled");
        }
        if (isSetupComplete()) {
            throw new IllegalStateException("Admin account already exists");
        }
        AdminUser admin = new AdminUser("admin", passwordEncoder.encode(password));
        adminUserRepository.save(admin);
    }

    public void changePassword(String newPassword) {
        if (isOAuth2AuthEnabled()) {
            throw new IllegalStateException("Password change is not available when OAuth2 authentication is enabled");
        }
        AdminUser admin = adminUserRepository.findByUsername("admin")
                .orElseThrow(() -> new IllegalStateException("Admin account not found"));
        admin.setPassword(passwordEncoder.encode(newPassword));
        adminUserRepository.save(admin);
    }

    public Optional<UserDetails> loadUserByUsername(String username) {
        return adminUserRepository.findByUsername(username)
                .map(admin -> new User(
                        admin.getUsername(),
                        admin.getPassword(),
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                ));
    }
}
