package org.remus.resticexplorer.admin;

import lombok.RequiredArgsConstructor;
import org.remus.resticexplorer.admin.data.AdminUser;
import org.remus.resticexplorer.admin.data.AdminUserRepository;
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

    public boolean isSetupComplete() {
        return adminUserRepository.count() > 0;
    }

    public void createAdmin(String password) {
        if (isSetupComplete()) {
            throw new IllegalStateException("Admin account already exists");
        }
        AdminUser admin = new AdminUser("admin", passwordEncoder.encode(password));
        adminUserRepository.save(admin);
    }

    public void changePassword(String newPassword) {
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
