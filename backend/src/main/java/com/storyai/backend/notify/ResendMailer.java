package com.storyai.backend.notify;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Resend(https://resend.com) HTTPS API 이메일 발송.
 * Railway가 SMTP 포트를 막아도 HTTPS(443)라 정상 발송된다. PDF 첨부(base64) 지원.
 *
 * 활성화(Railway 환경변수):
 *   RESEND_API_KEY = re_xxx (Resend 대시보드 API Keys)
 *   RESEND_FROM    = TodayHero <no-reply@todayhero.co.kr>  (도메인 인증 후. 기본값은 테스트 발신자)
 */
@Slf4j
@Component
public class ResendMailer {

    private static final String ENDPOINT = "https://api.resend.com/emails";

    @Value("${RESEND_API_KEY:}")
    private String apiKey;

    /** 발신 주소. 도메인 인증 전에는 Resend 테스트 발신자(onboarding@resend.dev)로 본인에게만 발송 가능. */
    @Value("${RESEND_FROM:TodayHero <onboarding@resend.dev>}")
    private String from;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)).build();

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** 이메일 발송. pdf 가 있으면 첨부한다. 실패 시 예외를 던진다. */
    public void send(String to, String subject, String text, byte[] pdf, String pdfName) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("RESEND_API_KEY 미설정");
        }
        StringBuilder json = new StringBuilder();
        json.append("{")
                .append("\"from\":\"").append(esc(from)).append("\",")
                .append("\"to\":[\"").append(esc(to)).append("\"],")
                .append("\"subject\":\"").append(esc(subject)).append("\",")
                .append("\"text\":\"").append(esc(text)).append("\"");
        if (pdf != null && pdf.length > 0) {
            String b64 = Base64.getEncoder().encodeToString(pdf);
            String name = (pdfName == null || pdfName.isBlank()) ? "동화책.pdf" : pdfName;
            json.append(",\"attachments\":[{\"filename\":\"").append(esc(name))
                    .append("\",\"content\":\"").append(b64).append("\"}]");
        }
        json.append("}");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + apiKey.trim())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.toString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() / 100 != 2) {
            throw new IllegalStateException("Resend 발송 실패(HTTP " + res.statusCode() + "): " + res.body());
        }
        log.info("Resend 발송 완료: to={}, status={}", to, res.statusCode());
    }

    /** JSON 문자열 이스케이프. */
    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
