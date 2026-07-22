package com.storyai.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Anthropic Claude(Messages API) 클라이언트 — 스토리(글) 생성용.
 * 이미지는 Gemini, 글은 Claude로 쓰는 하이브리드. 키는 ANTHROPIC_API_KEY 환경변수로 주입.
 * 미설정 시 isConfigured()=false → 호출부가 Gemini로 폴백.
 */
@Slf4j
@Component
public class ClaudeClient {

    private static final String ENDPOINT = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final String apiKey;
    private final String model;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public ClaudeClient(@Value("${ANTHROPIC_API_KEY:}") String apiKey,
                        @Value("${storyai.ai.claude.model:claude-opus-4-8}") String model,
                        ObjectMapper mapper) {
        this.apiKey = apiKey;
        this.model = model;
        this.mapper = mapper;
    }

    @PostConstruct
    void logConfig() {
        log.info("ClaudeClient 설정: configured={}, model={}", isConfigured(), model);
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** system 지시 + user 프롬프트로 JSON 응답을 받아 파싱한다. */
    public JsonNode generateJson(String system, String prompt) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", 8192);
        if (system != null && !system.isBlank()) {
            body.put("system", system);
        }
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "user").put("content", prompt);

        JsonNode resp = post(body);
        // content 배열의 첫 text 블록을 꺼낸다.
        String text = "";
        for (JsonNode block : resp.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                text = block.path("text").asText("");
                break;
            }
        }
        text = stripFences(text);
        try {
            return mapper.readTree(text);
        } catch (Exception e) {
            throw new IllegalStateException("Claude JSON 파싱 실패: " + e.getMessage()
                    + " (앞부분: " + text.substring(0, Math.min(120, text.length())) + ")", e);
        }
    }

    /** 일시적 오류(429/5xx·네트워크)는 지수 백오프로 재시도. */
    private JsonNode post(JsonNode body) {
        if (!isConfigured()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY가 설정되지 않았습니다.");
        }
        int maxAttempts = 4;
        IllegalStateException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(ENDPOINT))
                        .timeout(Duration.ofSeconds(120))
                        .header("x-api-key", apiKey)
                        .header("anthropic-version", ANTHROPIC_VERSION)
                        .header("content-type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int sc = res.statusCode();
                if (sc == 200) {
                    return mapper.readTree(res.body());
                }
                boolean retryable = sc == 429 || sc >= 500;
                last = new IllegalStateException("Claude HTTP " + sc + ": "
                        + res.body().substring(0, Math.min(200, res.body().length())));
                if (!retryable) {
                    throw last;
                }
                log.warn("Claude 호출 실패(HTTP {}), 재시도 {}/{}", sc, attempt, maxAttempts);
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                last = new IllegalStateException("Claude 호출 오류: " + e.getMessage(), e);
                log.warn("Claude 호출 예외, 재시도 {}/{}: {}", attempt, maxAttempts, e.getMessage());
            }
            try {
                Thread.sleep(500L * attempt);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw last != null ? last : new IllegalStateException("Claude 호출 실패");
    }

    /** 혹시 모델이 ```json ... ``` 코드펜스로 감쌌을 때 벗겨낸다. */
    private String stripFences(String s) {
        String t = s.strip();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl > 0) {
                t = t.substring(nl + 1);
            }
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3);
            }
        }
        return t.strip();
    }
}
