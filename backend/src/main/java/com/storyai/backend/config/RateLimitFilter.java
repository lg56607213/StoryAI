package com.storyai.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 아주 단순한 IP당 일일 "만들기" 요청 제한 (비용 폭탄 방지).
 * POST /api/video-jobs (새 생성)만 대상. jobs-per-day=0 이면 비활성(개발 기본).
 * 주의: 인메모리라 재시작 시 초기화되고 단일 인스턴스 기준이다. MVP용이며, 규모가 커지면
 * Redis/DB 기반 또는 로그인 사용자 기준으로 교체할 것.
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    @Value("${storyai.rate-limit.jobs-per-day:0}")
    private int jobsPerDay;

    private final Map<String, DayCount> counts = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (jobsPerDay > 0 && "POST".equalsIgnoreCase(request.getMethod())
                && "/api/video-jobs".equals(request.getRequestURI())) {
            String ip = clientIp(request);
            int used = counts.computeIfAbsent(ip, k -> new DayCount()).incrementFor(LocalDate.now());
            if (used > jobsPerDay) {
                response.setStatus(429); // Too Many Requests
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                        "{\"error\":\"오늘 만들기 요청 한도를 초과했어요. 내일 다시 시도해 주세요.\"}");
                log.warn("요청 제한 초과: ip={}, used={}/{}", ip, used, jobsPerDay);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For"); // 프록시(Railway) 뒤의 실제 IP
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** IP별 "오늘 날짜 + 카운트". 날짜가 바뀌면 리셋. */
    private static final class DayCount {
        private LocalDate day = LocalDate.MIN;
        private int count;

        synchronized int incrementFor(LocalDate today) {
            if (!today.equals(day)) {
                day = today;
                count = 0;
            }
            return ++count;
        }
    }
}
