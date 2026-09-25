package org.remus.resticexplorer.config;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class ResticOidcUserService extends OidcUserService {

    private final AuthProperties authProperties;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUserRequest request = withUserNameAttribute(userRequest);
        OidcUser oidcUser = super.loadUser(request);

        String provider = request.getClientRegistration().getRegistrationId();
        Set<GrantedAuthority> authorities = OAuth2UserAuthorityMapper.mapAuthorities(
                oidcUser.getAuthorities(), provider, authProperties);

        return new DefaultOidcUser(
                authorities,
                oidcUser.getIdToken(),
                oidcUser.getUserInfo(),
                request.getClientRegistration().getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName()
        );
    }

    /**
     * OIDC providers usually leave "user-name-attribute" unset, because "sub" is implied by the ID Token.
     * The default user services need a value as soon as the UserInfo endpoint is called, and DefaultOidcUser
     * rejects an empty name attribute key, so complete such a registration with the OIDC default before
     * delegating to them.
     */
    private static OidcUserRequest withUserNameAttribute(OidcUserRequest userRequest) {
        ClientRegistration registration = userRequest.getClientRegistration();
        if (StringUtils.hasText(registration.getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName())) {
            return userRequest;
        }
        ClientRegistration registrationWithSubject = ClientRegistration.withClientRegistration(registration)
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .build();
        return new OidcUserRequest(registrationWithSubject, userRequest.getAccessToken(), userRequest.getIdToken());
    }
}
