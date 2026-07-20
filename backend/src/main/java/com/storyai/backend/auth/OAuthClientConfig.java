package com.storyai.backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 소셜 로그인 클라이언트 등록을 코드로 구성한다.
 * 이 빈은 GOOGLE_CLIENT_ID 또는 KAKAO_CLIENT_ID 중 하나라도 설정됐을 때만 생성된다(@ConditionalOnExpression).
 * → 키가 전혀 없으면 빈 자체가 없어 로그인 비활성 + 앱 정상 부팅.
 */
@Configuration
@ConditionalOnExpression("'${GOOGLE_CLIENT_ID:}'.length() > 0 or '${KAKAO_CLIENT_ID:}'.length() > 0")
public class OAuthClientConfig {

    @Value("${GOOGLE_CLIENT_ID:}")
    private String googleId;
    @Value("${GOOGLE_CLIENT_SECRET:}")
    private String googleSecret;
    @Value("${KAKAO_CLIENT_ID:}")
    private String kakaoId;
    @Value("${KAKAO_CLIENT_SECRET:}")
    private String kakaoSecret;

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        // 환경변수에 실수로 붙은 앞뒤 공백/개행 제거(흔한 401 원인).
        googleId = trim(googleId);
        googleSecret = trim(googleSecret);
        kakaoId = trim(kakaoId);
        kakaoSecret = trim(kakaoSecret);

        List<ClientRegistration> regs = new ArrayList<>();
        if (StringUtils.hasText(googleId)) {
            regs.add(CommonOAuth2Provider.GOOGLE.getBuilder("google")
                    .clientId(googleId).clientSecret(googleSecret).build());
        }
        if (StringUtils.hasText(kakaoId)) {
            regs.add(kakao());
        }
        // 값은 남기지 않고, 환경변수가 제대로 로드됐는지(길이)만 로그로 확인.
        org.slf4j.LoggerFactory.getLogger(OAuthClientConfig.class).info(
                "OAuth 설정: googleId={}, kakaoId 길이={}, kakaoSecret 길이={}",
                StringUtils.hasText(googleId) ? "설정됨" : "없음",
                kakaoId == null ? 0 : kakaoId.trim().length(),
                kakaoSecret == null ? 0 : kakaoSecret.trim().length());
        return new InMemoryClientRegistrationRepository(regs);
    }

    private String trim(String s) {
        return s == null ? null : s.trim();
    }

    /** 카카오는 표준 provider가 아니라 직접 구성한다. */
    private ClientRegistration kakao() {
        return ClientRegistration.withRegistrationId("kakao")
                .clientId(kakaoId)
                .clientSecret(kakaoSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                // 이메일(account_email)은 카카오 비즈 앱 승인이 필요 → 우선 닉네임만.
                .scope("profile_nickname")
                .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                .tokenUri("https://kauth.kakao.com/oauth/token")
                .userInfoUri("https://kapi.kakao.com/v2/user/me")
                .userNameAttributeName("id")
                .clientName("Kakao")
                .build();
    }
}
