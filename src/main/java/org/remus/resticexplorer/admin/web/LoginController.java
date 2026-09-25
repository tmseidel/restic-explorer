package org.remus.resticexplorer.admin.web;

import org.remus.resticexplorer.admin.AdminService;
import org.remus.resticexplorer.config.AuthProperties;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Controller
public class LoginController {

    private final AdminService adminService;
    private final AuthProperties authProperties;
    private final Optional<ClientRegistrationRepository> clientRegistrationRepository;

    public LoginController(AdminService adminService, AuthProperties authProperties,
                           Optional<ClientRegistrationRepository> clientRegistrationRepository) {
        this.adminService = adminService;
        this.authProperties = authProperties;
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("localAuthEnabled", adminService.isLocalAuthEnabled());
        model.addAttribute("oauth2AuthEnabled", adminService.isOAuth2AuthEnabled());
        model.addAttribute("oauth2Providers", resolveOAuth2Providers());
        return "admin/login";
    }

    private List<String> resolveOAuth2Providers() {
        List<String> providers = new ArrayList<>();
        if (clientRegistrationRepository.isEmpty()) {
            return providers;
        }
        ClientRegistrationRepository repository = clientRegistrationRepository.get();
        if (authProperties.getAllowedProviders() != null && !authProperties.getAllowedProviders().isBlank()) {
            for (String p : authProperties.getAllowedProviders().split(",")) {
                String trimmed = p.trim();
                if (!trimmed.isEmpty() && repository.findByRegistrationId(trimmed) != null) {
                    providers.add(trimmed);
                }
            }
            return providers;
        }
        if (repository instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item instanceof ClientRegistration registration) {
                    providers.add(registration.getRegistrationId());
                }
            }
        }
        return providers;
    }
}
