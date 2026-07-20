package com.storyai.backend.auth;

import com.storyai.backend.domain.user.User;
import com.storyai.backend.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 소셜 로그인 성공 시 사용자 정보를 파싱해 DB에 저장/갱신한다.
 * 구글/카카오의 응답 구조가 달라서 provider별로 필드를 뽑는다.
 */
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest req) {
        OAuth2User oAuth2User = super.loadUser(req);
        String provider = req.getClientRegistration().getRegistrationId(); // google | kakao
        Map<String, Object> attrs = oAuth2User.getAttributes();

        String providerId;
        String email = null;
        String name = null;

        if ("kakao".equals(provider)) {
            providerId = String.valueOf(attrs.get("id"));
            Object account = attrs.get("kakao_account");
            if (account instanceof Map<?, ?> acc) {
                Object e = acc.get("email");
                email = e != null ? e.toString() : null;
                Object profile = acc.get("profile");
                if (profile instanceof Map<?, ?> p) {
                    Object nick = p.get("nickname");
                    name = nick != null ? nick.toString() : null;
                }
            }
        } else { // google 등 표준
            providerId = String.valueOf(attrs.getOrDefault("sub", attrs.get("id")));
            email = attrs.get("email") != null ? attrs.get("email").toString() : null;
            name = attrs.get("name") != null ? attrs.get("name").toString() : null;
        }

        final String em = email, nm = name, pid = providerId;
        userRepository.findByProviderAndProviderId(provider, pid)
                .map(u -> { u.setEmail(em); u.setName(nm); return userRepository.save(u); })
                .orElseGet(() -> userRepository.save(new User(provider, pid, em, nm)));

        String nameKey = req.getClientRegistration().getProviderDetails()
                .getUserInfoEndpoint().getUserNameAttributeName();
        return new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")), attrs, nameKey);
    }
}
