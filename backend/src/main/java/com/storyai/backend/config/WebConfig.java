package com.storyai.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS 설정. 배포 시 프론트(Netlify 도메인)에서 백엔드(Railway)로의 교차 출처 요청을 허용한다.
 * 허용 도메인은 storyai.cors.allowed-origins (env CORS_ALLOWED_ORIGINS)로 지정. 기본 "*"는 개발용.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${storyai.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = allowedOrigins.split("\\s*,\\s*");
        registry.addMapping("/api/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
