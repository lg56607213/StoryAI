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

    /**
     * 사용자 식별 키 — 마이페이지·하루 생성 제한·관리자 집계에서 "같은 사람"을 판별할 때 쓴다.
     * 이메일이 있으면 이메일, 없으면 제공자+계정ID로 대체한다.
     * (카카오는 이메일 동의항목이 없으면 이메일을 주지 않으므로 이메일만 쓰면 식별이 불가능해진다)
     */
    public static String identityOf(Authentication auth) {
        String email = emailOf(auth);
        if (email != null && !email.isBlank()) {
            return email;
        }
        if (!(auth instanceof OAuth2AuthenticationToken t)) {
            return null;
        }
        Map<String, Object> attrs = t.getPrincipal().getAttributes();
        Object id = attrs.get("id") != null ? attrs.get("id") : attrs.get("sub");
        return id != null ? t.getAuthorizedClientRegistrationId() + ":" + id : null;
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
