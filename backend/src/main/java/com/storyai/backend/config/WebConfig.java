package com.storyai.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS 설정 (CorsConfigurationSource 빈).
 * Spring Security 필터가 이 빈을 사용해 프론트(도메인)에서 백엔드로의 교차 출처 + 쿠키 요청을 허용한다.
 * 허용 도메인은 storyai.cors.allowed-origins (env CORS_ALLOWED_ORIGINS). 기본 "*"는 개발용.
 */
@Configuration
public class WebConfig {

    @Value("${storyai.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        // allowedOriginPatterns는 "*"라도 쿠키 허용과 함께 사용 가능(로그인 대비).
        cfg.setAllowedOriginPatterns(Arrays.asList(allowedOrigins.split("\\s*,\\s*")));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
