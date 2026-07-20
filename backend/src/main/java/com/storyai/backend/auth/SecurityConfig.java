package com.storyai.backend.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * 보안 설정.
 * - 모든 /api는 공개(permitAll) — 기존 익명 생성 흐름을 깨지 않는다. 로그인은 "부가" 기능.
 * - OAuth2 로그인은 클라이언트 등록(구글/카카오 키)이 있을 때만 활성화 → 키가 없어도 앱은 정상 부팅.
 * - CSRF 비활성(순수 API + SameSite 쿠키), CORS는 CorsConfigurationSource 빈 사용.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CorsConfigurationSource corsConfigurationSource;

    /** 로그인 성공/실패 후 돌아갈 프론트 주소. */
    @Value("${storyai.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations,
            ObjectProvider<CustomOAuth2UserService> oAuth2UserService) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .logout(logout -> logout
                        .logoutUrl("/api/logout")
                        .logoutSuccessHandler((req, res, a) -> res.setStatus(200))
                        .deleteCookies("JSESSIONID"));

        // 구글/카카오 키가 설정된 경우에만 OAuth2 로그인을 붙인다(키 없으면 부팅 안전).
        if (clientRegistrations.getIfAvailable() != null) {
            http.oauth2Login(oauth -> oauth
                    .userInfoEndpoint(u -> u.userService(oAuth2UserService.getObject()))
                    .successHandler((req, res, a) -> res.sendRedirect(frontendUrl))
                    .failureHandler((req, res, e) -> res.sendRedirect(frontendUrl + "?login=fail")));
        }

        return http.build();
    }
}
