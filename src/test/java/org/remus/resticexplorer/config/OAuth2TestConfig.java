package org.remus.resticexplorer.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;

@TestConfiguration
public class OAuth2TestConfig {

    @Bean
    @Primary
    public ClientRegistrationRepository testClientRegistrationRepository() {
        ClientRegistration keycloak = ClientRegistration.withRegistrationId("keycloak")
                .clientId("restic-explorer")
                .clientSecret("test-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri("https://keycloak.example.com/realms/master/protocol/openid-connect/auth")
                .tokenUri("https://keycloak.example.com/realms/master/protocol/openid-connect/token")
                .userInfoUri("https://keycloak.example.com/realms/master/protocol/openid-connect/userinfo")
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .clientName("Keycloak")
                .build();

        ClientRegistration entra = ClientRegistration.withRegistrationId("entra")
                .clientId("restic-explorer")
                .clientSecret("test-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri("https://login.microsoftonline.com/common/oauth2/v2.0/authorize")
                .tokenUri("https://login.microsoftonline.com/common/oauth2/v2.0/token")
                .userInfoUri("https://graph.microsoft.com/oidc/userinfo")
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .clientName("Microsoft Entra ID")
                .build();

        return new InMemoryClientRegistrationRepository(keycloak, entra);
    }
}
