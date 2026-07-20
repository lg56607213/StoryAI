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
        List<ClientRegistration> regs = new ArrayList<>();
        if (StringUtils.hasText(googleId)) {
            regs.add(CommonOAuth2Provider.GOOGLE.getBuilder("google")
                    .clientId(googleId).clientSecret(googleSecret).build());
        }
        if (StringUtils.hasText(kakaoId)) {
            regs.add(kakao());
        }
        return new InMemoryClientRegistrationRepository(regs);
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
