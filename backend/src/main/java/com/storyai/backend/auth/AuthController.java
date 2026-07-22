package com.storyai.backend.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import com.storyai.backend.notify.EmailNotifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
    private final EmailNotifier emailNotifier;
    private final AdminGuard adminGuard;

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        Map<String, Object> res = new HashMap<>();
        // 구글/카카오 키가 설정돼 로그인이 실제로 가능한지 → 프론트가 이때만 로그인 버튼 표시.
        res.put("loginEnabled", clientRegistrations.getIfAvailable() != null);
        res.put("isAdmin", adminGuard.isAdmin(authentication));
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

    /**
     * 메일 발송 테스트: 로그인한 "본인" 이메일로 테스트 메일을 보낸다.
     * (본인에게만 발송 → 스팸 악용 불가, 상시 유지해도 안전)
     */
    @PostMapping("/me/test-email")
    public Map<String, Object> testEmail(Authentication authentication) {
        Map<String, Object> res = new HashMap<>();
        res.put("mailConfigured", emailNotifier.isConfigured());
        if (!(authentication instanceof OAuth2AuthenticationToken token)) {
            res.put("sent", false);
            res.put("error", "로그인이 필요합니다.");
            return res;
        }
        String email = emailOf(token);
        res.put("to", email);
        if (email == null || email.isBlank()) {
            res.put("sent", false);
            res.put("error", "계정에서 이메일을 찾을 수 없어요(카카오는 이메일 동의 필요).");
            return res;
        }
        try {
            emailNotifier.sendSimple(email, "[투데이히어로] 메일 발송 테스트 ✉️",
                    "이 메일이 보이면 발송 설정이 정상입니다! 🎉\n실제 주문 완성 시 고객에게 이 주소로 PDF가 발송됩니다.\n\n— 투데이히어로");
            res.put("sent", true);
        } catch (Exception e) {
            res.put("sent", false);
            res.put("error", e.getMessage());
        }
        return res;
    }

    /** 로그인 토큰에서 이메일 추출(구글/카카오). */
    private String emailOf(OAuth2AuthenticationToken token) {
        Map<String, Object> attrs = token.getPrincipal().getAttributes();
        if ("kakao".equals(token.getAuthorizedClientRegistrationId())) {
            if (attrs.get("kakao_account") instanceof Map<?, ?> acc) {
                return str(acc.get("email"));
            }
            return null;
        }
        return str(attrs.get("email"));
    }

    private String str(Object o) {
        return o != null ? o.toString() : null;
    }
}
