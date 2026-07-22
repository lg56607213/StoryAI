package com.storyai.backend.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

import java.util.Map;

/**
 * 로그인 토큰에서 이메일/제공자를 추출하는 공용 헬퍼(구글/카카오).
 * 여러 곳(인증/주문/관리자)에서 재사용.
 */
public final class LoginIdentity {

    private LoginIdentity() {
    }

    public static String providerOf(Authentication auth) {
        return (auth instanceof OAuth2AuthenticationToken t) ? t.getAuthorizedClientRegistrationId() : null;
    }

    public static String emailOf(Authentication auth) {
        if (!(auth instanceof OAuth2AuthenticationToken t)) {
            return null;
        }
        Map<String, Object> attrs = t.getPrincipal().getAttributes();
        if ("kakao".equals(t.getAuthorizedClientRegistrationId())) {
            if (attrs.get("kakao_account") instanceof Map<?, ?> acc) {
                return str(acc.get("email"));
            }
            return null;
        }
        return str(attrs.get("email"));
    }

    public static String nameOf(Authentication auth) {
        if (!(auth instanceof OAuth2AuthenticationToken t)) {
            return null;
        }
        Map<String, Object> attrs = t.getPrincipal().getAttributes();
        if ("kakao".equals(t.getAuthorizedClientRegistrationId())) {
            if (attrs.get("kakao_account") instanceof Map<?, ?> acc
                    && acc.get("profile") instanceof Map<?, ?> p) {
                return str(p.get("nickname"));
            }
            return null;
        }
        return str(attrs.get("name"));
    }

    private static String str(Object o) {
        return o != null ? o.toString() : null;
    }
}
