package org.remus.resticexplorer.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.client.RestOperations;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for the principal rebuilt by {@link ResticOidcUserService} and
 * {@link ResticOAuth2UserService}: the authorities are mapped, but the name handling of the principal
 * returned by Spring has to survive - in particular for OIDC providers that do not configure
 * "user-name-attribute", because "sub" is implied by the ID Token.
 */
class OAuth2UserServiceTest {

    @Test
    void oidcRegistrationWithoutUserNameAttributeUsesSubject() {
        ResticOidcUserService service = new ResticOidcUserService(new AuthProperties());

        OidcUser user = service.loadUser(new OidcUserRequest(oidcRegistration().build(), accessToken(), idToken()));

        assertThat(user.getName()).isEqualTo("user-1");
        assertThat(authorities(user)).contains("ROLE_ADMIN");
    }

    @Test
    void oidcRegistrationWithoutUserNameAttributeStillLoadsUserInfo() {
        ResticOidcUserService service = new ResticOidcUserService(new AuthProperties());
        service.setOauth2UserService(userInfoService(Map.of(
                IdTokenClaimNames.SUB, "user-1",
                "preferred_username", "tm",
                "email", "tm@example.com")));

        OidcUser user = service.loadUser(new OidcUserRequest(
                oidcRegistration().userInfoUri("https://keycloak.example.com/userinfo").build(),
                accessToken(),
                idToken()));

        assertThat(user.getName()).isEqualTo("user-1");
        assertThat(user.getUserInfo()).isNotNull();
        assertThat(user.getUserInfo().getEmail()).isEqualTo("tm@example.com");
        assertThat(authorities(user)).contains("ROLE_ADMIN");
    }

    @Test
    void oauth2RegistrationWithUserNameAttributeUsesThatAttribute() {
        ResticOAuth2UserService service = new ResticOAuth2UserService(new AuthProperties());
        service.setRestOperations(userInfoRestOperations(Map.of("login", "octocat", "id", 42)));

        ClientRegistration registration = ClientRegistration.withRegistrationId("github")
                .clientId("restic-explorer")
                .clientSecret("test-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("read:user")
                .authorizationUri("https://github.com/login/oauth/authorize")
                .tokenUri("https://github.com/login/oauth/access_token")
                .userInfoUri("https://api.github.com/user")
                .userNameAttributeName("login")
                .clientName("GitHub")
                .build();

        OAuth2User user = service.loadUser(new OAuth2UserRequest(registration, accessToken()));

        assertThat(user.getName()).isEqualTo("octocat");
        assertThat(user.getAttributes()).containsEntry("id", 42);
        assertThat(authorities(user)).contains("ROLE_ADMIN", "OAUTH2_USER");
    }

    @Test
    void oauth2RegistrationOfDisallowedProviderIsRejected() {
        AuthProperties authProperties = new AuthProperties();
        authProperties.setAllowedProviders("keycloak");
        ResticOAuth2UserService service = new ResticOAuth2UserService(authProperties);
        service.setRestOperations(userInfoRestOperations(Map.of("login", "octocat")));

        ClientRegistration registration = ClientRegistration.withRegistrationId("github")
                .clientId("restic-explorer")
                .clientSecret("test-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("read:user")
                .authorizationUri("https://github.com/login/oauth/authorize")
                .tokenUri("https://github.com/login/oauth/access_token")
                .userInfoUri("https://api.github.com/user")
                .userNameAttributeName("login")
                .build();

        OAuth2UserRequest userRequest = new OAuth2UserRequest(registration, accessToken());

        assertThatThrownBy(() -> service.loadUser(userRequest)).isInstanceOf(OAuth2AuthenticationException.class);
    }

    private static ClientRegistration.Builder oidcRegistration() {
        return ClientRegistration.withRegistrationId("keycloak")
                .clientId("restic-explorer")
                .clientSecret("test-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope(OidcScopes.OPENID, OidcScopes.PROFILE)
                .authorizationUri("https://keycloak.example.com/realms/master/protocol/openid-connect/auth")
                .tokenUri("https://keycloak.example.com/realms/master/protocol/openid-connect/token")
                .clientName("Keycloak");
    }

    private static DefaultOAuth2UserService userInfoService(Map<String, Object> userInfoAttributes) {
        DefaultOAuth2UserService userInfoService = new DefaultOAuth2UserService();
        userInfoService.setRestOperations(userInfoRestOperations(userInfoAttributes));
        return userInfoService;
    }

    private static RestOperations userInfoRestOperations(Map<String, Object> userInfoAttributes) {
        RestOperations restOperations = mock(RestOperations.class);
        doReturn(ResponseEntity.ok(userInfoAttributes)).when(restOperations)
                .exchange(any(RequestEntity.class), any(ParameterizedTypeReference.class));
        return restOperations;
    }

    private static OAuth2AccessToken accessToken() {
        Instant now = Instant.now();
        return new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-token", now, now.plusSeconds(300));
    }

    private static OidcIdToken idToken() {
        Instant now = Instant.now();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put(IdTokenClaimNames.SUB, "user-1");
        claims.put(IdTokenClaimNames.ISS, "https://keycloak.example.com/realms/master");
        claims.put(IdTokenClaimNames.IAT, now);
        claims.put(IdTokenClaimNames.EXP, now.plusSeconds(300));
        claims.put("preferred_username", "tm");
        return new OidcIdToken("id-token", now, now.plusSeconds(300), claims);
    }

    private static Iterable<String> authorities(OAuth2User user) {
        return user.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }
}
