package org.remus.resticexplorer.config;

import org.remus.resticexplorer.admin.AdminService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

import java.util.Optional;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AuthProperties authProperties;
    private final Optional<ClientRegistrationRepository> clientRegistrationRepository;

    public SecurityConfig(AuthProperties authProperties, Optional<ClientRegistrationRepository> clientRegistrationRepository) {
        this.authProperties = authProperties;
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(AdminService adminService) {
        return username -> adminService.loadUserByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AdminService adminService,
                                           ResticOAuth2UserService resticOAuth2UserService,
                                           ResticOidcUserService resticOidcUserService) throws Exception {
        if (authProperties.getMode() == AuthProperties.AuthMode.OAUTH2) {
            if (clientRegistrationRepository.isEmpty()) {
                throw new IllegalStateException(
                        "OAuth2 authentication is enabled (restic.auth.mode=oauth2) but no OAuth2 client registrations are configured. "
                                + "Add spring.security.oauth2.client.registration.* properties for your provider (e.g. Keycloak or Entra ID)."
                );
            }
            configureOAuth2(http, resticOAuth2UserService, resticOidcUserService);
        } else {
            configureLocal(http);
        }
        return http.build();
    }

    private void configureAuthorization(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/setup/**").permitAll()
                .requestMatchers("/css/**", "/js/**", "/images/**").permitAll()
                .requestMatchers("/api/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/repositories/new", "/repositories/*/edit", "/repositories/*/delete", "/repositories/*/scan", "/repositories/*/check", "/repositories/*/unlock").hasRole("ADMIN")
                .requestMatchers("/groups/**").hasRole("ADMIN")
                .requestMatchers("/download/**").hasRole("ADMIN")
                .anyRequest().permitAll()
        );
    }

    private void configureLocal(HttpSecurity http) throws Exception {
        configureAuthorization(http);
        http
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/")
                .permitAll()
            );
    }

    private void configureOAuth2(HttpSecurity http, ResticOAuth2UserService resticOAuth2UserService,
                                 ResticOidcUserService resticOidcUserService) throws Exception {
        configureAuthorization(http);
        http
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                .userInfoEndpoint(userInfo -> userInfo
                    .oidcUserService(resticOidcUserService)
                    .userService(resticOAuth2UserService)
                )
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/")
                .permitAll()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"))
            );
    }
}
