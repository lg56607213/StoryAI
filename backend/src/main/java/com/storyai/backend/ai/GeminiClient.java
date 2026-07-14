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
import java.util.Base64;
import java.util.List;

/**
 * Google Gemini(Generative Language) API 클라이언트.
 * - 텍스트: 스토리 생성 (generateJson / generateText)
 * - 이미지: 나노바나나(Flash Image)로 삽화 생성 (generateImage), 참조 이미지로 캐릭터 일관성 유지
 * 키는 GEMINI_API_KEY 환경변수(→ application.yml)로 주입. 미설정 시 isConfigured()=false.
 */
@Slf4j
@Component
public class GeminiClient {

    private static final String BASE = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final String apiKey;
    private final String textModel;
    private final String imageModel;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public GeminiClient(@Value("${storyai.ai.gemini.api-key:}") String apiKey,
                        @Value("${storyai.ai.gemini.text-model:gemini-flash-latest}") String textModel,
                        @Value("${storyai.ai.gemini.image-model:gemini-3.1-flash-image}") String imageModel,
                        ObjectMapper mapper) {
        this.apiKey = apiKey;
        this.textModel = textModel;
        this.imageModel = imageModel;
        this.mapper = mapper;
    }

    @PostConstruct
    void logConfig() {
        log.info("GeminiClient 설정: configured={}, textModel={}, imageModel={}",
                isConfigured(), textModel, imageModel);
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** JSON 응답을 강제(responseMimeType)해서 파싱된 트리를 반환. */
    public JsonNode generateJson(String prompt) {
        ObjectNode body = mapper.createObjectNode();
        body.putArray("contents").addObject().putArray("parts").addObject().put("text", prompt);
        body.putObject("generationConfig").put("responseMimeType", "application/json");
        String text = firstText(post(textModel, body));
        try {
            return mapper.readTree(text);
        } catch (Exception e) {
            throw new IllegalStateException("Gemini JSON 파싱 실패: " + text, e);
        }
    }

    public String generateText(String prompt) {
        ObjectNode body = mapper.createObjectNode();
        body.putArray("contents").addObject().putArray("parts").addObject().put("text", prompt);
        return firstText(post(textModel, body));
    }

    /** 텍스트(+선택 참조 이미지)로 이미지를 생성해 PNG 바이트를 반환. */
    public byte[] generateImage(String prompt, List<byte[]> referenceImages) {
        return generateImage(prompt, referenceImages, null);
    }

    /**
     * 이미지 생성. aspectRatio 지정 시 가로/세로 비율을 강제한다(예: "3:2" 가로, "2:3" 세로).
     * null이면 모델 기본값.
     */
    public byte[] generateImage(String prompt, List<byte[]> referenceImages, String aspectRatio) {
        ObjectNode body = mapper.createObjectNode();
        ArrayNode parts = body.putArray("contents").addObject().putArray("parts");
        if (referenceImages != null) {
            for (byte[] img : referenceImages) {
                ObjectNode inline = parts.addObject().putObject("inlineData");
                inline.put("mimeType", "image/png");
                inline.put("data", Base64.getEncoder().encodeToString(img));
            }
        }
        parts.addObject().put("text", prompt);
        if (aspectRatio != null && !aspectRatio.isBlank()) {
            body.putObject("generationConfig").putObject("imageConfig").put("aspectRatio", aspectRatio);
        }

        JsonNode resp = post(imageModel, body);
        for (JsonNode p : resp.path("candidates").path(0).path("content").path("parts")) {
            String data = p.path("inlineData").path("data").asText("");
            if (!data.isEmpty()) {
                return Base64.getDecoder().decode(data);
            }
        }
        throw new IllegalStateException("Gemini 이미지 응답에 이미지 없음: finishReason="
                + resp.path("candidates").path(0).path("finishReason").asText());
    }

    /**
     * Gemini 호출. 일시적 오류(429/5xx·네트워크)는 지수 백오프로 재시도한다.
     * 재시도 불가능한 4xx(잘못된 요청 등)는 즉시 실패. 이미지 삽화 503 실패로 빈 페이지가 나던 문제를 완화.
     */
    private JsonNode post(String model, JsonNode body) {
        if (!isConfigured()) {
            throw new IllegalStateException("GEMINI_API_KEY가 설정되지 않았습니다.");
        }
        int maxAttempts = 4;
        IllegalStateException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(BASE + model + ":generateContent"))
                        .timeout(Duration.ofSeconds(180))
                        .header("x-goog-api-key", apiKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int sc = res.statusCode();
                if (sc == 200) {
                    return mapper.readTree(res.body());
                }
                last = new IllegalStateException("Gemini API " + sc + ": " + res.body());
                boolean retryable = sc == 429 || sc >= 500; // 과부하/일시 오류만 재시도
                if (!retryable) {
                    throw last;
                }
                log.warn("Gemini {} 일시 오류 (시도 {}/{}) → 재시도", sc, attempt, maxAttempts);
            } catch (IllegalStateException e) {
                throw e; // 재시도 불가(4xx)
            } catch (Exception e) {
                last = new IllegalStateException("Gemini 호출 실패: " + e.getMessage(), e);
                log.warn("Gemini 네트워크 오류 (시도 {}/{}): {}", attempt, maxAttempts, e.getMessage());
            }
            if (attempt < maxAttempts) {
                sleepMs(700L * (1L << (attempt - 1))); // 700ms, 1.4s, 2.8s
            }
        }
        throw last;
    }

    private void sleepMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private String firstText(JsonNode resp) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode p : resp.path("candidates").path(0).path("content").path("parts")) {
            if (p.has("text")) {
                sb.append(p.get("text").asText());
            }
        }
        if (sb.length() == 0) {
            throw new IllegalStateException("Gemini 텍스트 응답 비어있음: finishReason="
                    + resp.path("candidates").path(0).path("finishReason").asText());
        }
        return sb.toString();
    }
}
