package org.remus.resticexplorer.config;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class OAuth2UserAuthorityMapper {

    private OAuth2UserAuthorityMapper() {
    }

    public static Set<GrantedAuthority> mapAuthorities(Collection<? extends GrantedAuthority> originalAuthorities, String provider, AuthProperties authProperties) {
        validateProvider(provider, authProperties);
        Set<GrantedAuthority> authorities = new HashSet<>(originalAuthorities);
        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        return authorities;
    }

    private static void validateProvider(String provider, AuthProperties authProperties) {
        if (authProperties.getAllowedProviders() == null || authProperties.getAllowedProviders().isBlank()) {
            return;
        }
        Set<String> allowed = new HashSet<>();
        for (String p : authProperties.getAllowedProviders().split(",")) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) {
                allowed.add(trimmed);
            }
        }
        if (!allowed.isEmpty() && !allowed.contains(provider)) {
            throw new OAuth2AuthenticationException("OAuth2 provider '" + provider + "' is not allowed");
        }
    }
}
