package org.remus.resticexplorer.config;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class ResticOAuth2UserService extends DefaultOAuth2UserService {

    private final AuthProperties authProperties;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String provider = userRequest.getClientRegistration().getRegistrationId();
        Set<GrantedAuthority> authorities = OAuth2UserAuthorityMapper.mapAuthorities(
                oAuth2User.getAuthorities(), provider, authProperties);

        return new DefaultOAuth2User(
                authorities,
                oAuth2User.getAttributes(),
                nameAttributeKey(userRequest, oAuth2User)
        );
    }

    /**
     * Generic OAuth2 providers have no implied user name attribute, so the configured "user-name-attribute" is
     * only usable once DefaultOAuth2UserService has matched it against the UserInfo response. Read the key it
     * resolved back from the loaded principal's authority instead of re-reading the client registration, and
     * fall back to the configured value for a principal that carries no such authority.
     */
    private static String nameAttributeKey(OAuth2UserRequest userRequest, OAuth2User oAuth2User) {
        return oAuth2User.getAuthorities().stream()
                .filter(OAuth2UserAuthority.class::isInstance)
                .map(OAuth2UserAuthority.class::cast)
                .map(OAuth2UserAuthority::getUserNameAttributeName)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElseGet(() -> userRequest.getClientRegistration()
                        .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName());
    }
}
