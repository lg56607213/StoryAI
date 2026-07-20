package com.storyai.backend.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 로그인 상태 조회. 프론트가 이 결과로 "로그인/로그아웃" 버튼과 이름을 표시한다.
 * (로그아웃은 SecurityConfig의 /api/logout 이 처리)
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        Map<String, Object> res = new HashMap<>();
        // 구글/카카오 키가 설정돼 로그인이 실제로 가능한지 → 프론트가 이때만 로그인 버튼 표시.
        res.put("loginEnabled", clientRegistrations.getIfAvailable() != null);
        if (authentication instanceof OAuth2AuthenticationToken token) {
            String provider = token.getAuthorizedClientRegistrationId();
            OAuth2User u = token.getPrincipal();
            Map<String, Object> attrs = u.getAttributes();

            String email = null, name = null;
            if ("kakao".equals(provider)) {
                Object account = attrs.get("kakao_account");
                if (account instanceof Map<?, ?> acc) {
                    email = str(acc.get("email"));
                    if (acc.get("profile") instanceof Map<?, ?> p) {
                        name = str(p.get("nickname"));
                    }
                }
            } else {
                email = str(attrs.get("email"));
                name = str(attrs.get("name"));
            }
            res.put("authenticated", true);
            res.put("provider", provider);
            res.put("name", name);
            res.put("email", email);
        } else {
            res.put("authenticated", false);
        }
        return res;
    }

    private String str(Object o) {
        return o != null ? o.toString() : null;
    }
}
