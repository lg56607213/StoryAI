package com.storyai.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 테스트용 ID/PW 로그인(카드사 심사·데모용). 구글 OAuth와 별개로, 지정한 테스트 계정으로 접속할 수 있게 한다.
 * <p>보안: 계정은 환경변수(TEST_LOGIN_ID/PW)로만 설정한다. 둘 다 없으면 이 기능은 비활성(로그인 거부).
 * <p>로그인 성공 시, 앱 전역이 쓰는 {@link OAuth2AuthenticationToken}(provider="test")를 만들어 세션에 저장한다.
 * 이렇게 하면 {@link LoginIdentity}·마이페이지·주문·관리자 로직이 구글 로그인과 동일하게 동작한다.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class TestAuthController {

    private final HttpSessionSecurityContextRepository securityContextRepository;

    @Value("${storyai.auth.test.username:}")
    private String testUsername;
    @Value("${storyai.auth.test.password:}")
    private String testPassword;
    @Value("${storyai.auth.test.email:tester@todayhero.co.kr}")
    private String testEmail;
    @Value("${storyai.auth.test.name:심사 테스터}")
    private String testName;

    /** 테스트 로그인 사용 가능 여부(환경변수 설정됨). 프론트가 이 값으로 ID/PW 폼을 노출. */
    public boolean enabled() {
        return testUsername != null && !testUsername.isBlank()
                && testPassword != null && !testPassword.isBlank();
    }

    public record LoginRequest(String username, String password) {
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody LoginRequest req,
            HttpServletRequest request,
            HttpServletResponse response) {

        if (!enabled()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "테스트 로그인이 비활성화되어 있습니다."));
        }
        String u = req.username() == null ? "" : req.username().trim();
        String p = req.password() == null ? "" : req.password();
        if (!testUsername.equals(u) || !testPassword.equals(p)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "아이디 또는 비밀번호가 올바르지 않습니다."));
        }

        // 앱 전역이 쓰는 형태(OAuth2AuthenticationToken, provider="test")로 인증을 만들어 세션에 저장.
        Map<String, Object> attrs = Map.of(
                "sub", "test:" + testUsername,
                "email", testEmail,
                "name", testName);
        OAuth2User principal = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")), attrs, "sub");
        OAuth2AuthenticationToken auth =
                new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "test");

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response); // 세션에 저장 → 쿠키로 유지

        return ResponseEntity.ok(Map.of(
                "authenticated", true,
                "provider", "test",
                "email", testEmail,
                "name", testName));
    }
}
