package org.remus.resticexplorer.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "restic.auth")
public class AuthProperties {

    public enum AuthMode {
        LOCAL,
        OAUTH2
    }

    private AuthMode mode = AuthMode.LOCAL;

    /**
     * Comma-separated list of OAuth2 providers that are allowed to log in.
     * If empty, any configured provider is allowed.
     */
    private String allowedProviders = "";
}
