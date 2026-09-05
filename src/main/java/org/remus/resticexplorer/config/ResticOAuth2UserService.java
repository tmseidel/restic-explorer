package org.remus.resticexplorer.config;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

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

        String nameAttributeKey = userRequest.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();

        return new DefaultOAuth2User(
                authorities,
                oAuth2User.getAttributes(),
                nameAttributeKey
        );
    }
}
