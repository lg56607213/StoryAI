package com.storyai.backend.ai.image;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * fal.ai 삽화 생성기 — 얼굴 일관성이 강한 멀티 레퍼런스 편집 모델(FLUX.2 pro edit 등)을 사용한다.
 * storyai.ai.image-provider=fal 일 때만 활성화되며, 프롬프트는 Gemini와 동일한 {@link ImagePrompts}를 쓴다.
 *
 * 참조 이미지는 base64 data URI로 인라인 전송하므로 별도의 공개 URL이 필요 없다.
 * 응답의 images[0].url 을 내려받아 PNG 바이트로 반환한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "storyai.ai.image-provider", havingValue = "fal")
public class FalImageGenerator implements ImageGenerator {

    private static final String BASE = "https://fal.run/";

    private final String apiKey;
    private final String editModel;
    private final String textModel;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    /** 성공한 이미지 생성 횟수(비용 추적용). */
    private static final AtomicLong imageGenCount = new AtomicLong();

    public FalImageGenerator(@Value("${storyai.ai.fal.api-key:}") String apiKey,
                             @Value("${storyai.ai.fal.edit-model:fal-ai/flux-2-pro/edit}") String editModel,
                             @Value("${storyai.ai.fal.text-model:fal-ai/flux-2-pro}") String textModel,
                             ObjectMapper mapper) {
        this.apiKey = apiKey;
        this.editModel = editModel;
        this.textModel = textModel;
        this.mapper = mapper;
    }

    @PostConstruct
    void logConfig() {
        log.info("FalImageGenerator 활성화: configured={}, editModel={}, textModel={}",
                isAvailable(), editModel, textModel);
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** 캐릭터 시트: 정사각. 페이지 삽화: 가로 3:2에 가깝게(출력 1MP 이하로 두어 과금 절약). */
    private static final int SHEET_W = 1024, SHEET_H = 1024;
    private static final int PAGE_W = 1216, PAGE_H = 816;

    @Override
    public byte[] everydaySheet(List<byte[]> photos, String name, String style) {
        return edit(ImagePrompts.everydaySheet(name, style), photos, SHEET_W, SHEET_H);
    }

    @Override
    public byte[] costumeSheet(byte[] everydaySheet, String costume, String style) {
        return edit(ImagePrompts.costumeSheet(costume, style), List.of(everydaySheet), SHEET_W, SHEET_H);
    }

    @Override
    public byte[] mascotSheet(String appearance, String style) {
        // 참조 이미지 없음 → 텍스트→이미지 모델 사용.
        ObjectNode body = mapper.createObjectNode();
        body.put("prompt", ImagePrompts.mascotSheet(appearance, style));
        putImageSize(body, SHEET_W, SHEET_H);
        body.put("output_format", "png");
        body.put("num_images", 1);
        return downloadFirstImage(post(textModel, body));
    }

    @Override
    public byte[] illustrate(String scene, List<byte[]> characterSheets, String style) {
        return illustrateWithCompanion(scene, characterSheets, null, null, style);
    }

    @Override
    public byte[] illustrateWithCompanion(String scene, List<byte[]> characterSheets,
                                          byte[] companionSheet, String companionDesc, String style) {
        int n = characterSheets.size();
        List<byte[]> refs = new ArrayList<>(characterSheets);
        if (companionSheet != null) {
            refs.add(companionSheet);
        }
        String prompt = ImagePrompts.illustrate(scene, n, companionSheet != null ? companionDesc : null, style);
        return edit(prompt, refs, PAGE_W, PAGE_H);
    }

    // ---------- 내부 ----------

    /** 참조 이미지들을 data URI로 실어 편집 모델을 호출한다. */
    private byte[] edit(String prompt, List<byte[]> references, int width, int height) {
        ObjectNode body = mapper.createObjectNode();
        body.put("prompt", prompt);
        ArrayNode urls = body.putArray("image_urls");
        for (byte[] img : references) {
            if (img != null && img.length > 0) {
                urls.add("data:image/png;base64," + Base64.getEncoder().encodeToString(img));
            }
        }
        putImageSize(body, width, height);
        body.put("output_format", "png");
        body.put("num_images", 1);
        return downloadFirstImage(post(editModel, body));
    }

    /** FLUX.2는 aspect_ratio가 아니라 image_size(프리셋 또는 {width,height})를 사용한다. */
    private void putImageSize(ObjectNode body, int width, int height) {
        ObjectNode size = body.putObject("image_size");
        size.put("width", width);
        size.put("height", height);
    }

    /** fal 동기 호출. 일시적 오류(429/5xx·네트워크)는 지수 백오프로 재시도. */
    private JsonNode post(String model, JsonNode body) {
        if (!isAvailable()) {
            throw new IllegalStateException("FAL_KEY가 설정되지 않았습니다.");
        }
        int maxAttempts = 4;
        IllegalStateException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(BASE + model))
                        .timeout(Duration.ofSeconds(180))
                        .header("Authorization", "Key " + apiKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int sc = res.statusCode();
                if (sc == 200) {
                    return mapper.readTree(res.body());
                }
                last = new IllegalStateException("fal API " + sc + ": " + snippet(res.body()));
                if (!(sc == 429 || sc >= 500)) {
                    throw last; // 4xx는 재시도 무의미
                }
                log.warn("fal {} 일시 오류 (시도 {}/{}) → 재시도", sc, attempt, maxAttempts);
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                last = new IllegalStateException("fal 호출 실패: " + e.getMessage(), e);
                log.warn("fal 네트워크 오류 (시도 {}/{}): {}", attempt, maxAttempts, e.getMessage());
            }
            if (attempt < maxAttempts) {
                sleepMs(700L * (1L << (attempt - 1)));
            }
        }
        throw last;
    }

    /** 응답의 images[0].url 을 내려받아 바이트로 반환. */
    private byte[] downloadFirstImage(JsonNode resp) {
        String url = resp.path("images").path(0).path("url").asText("");
        if (url.isBlank()) {
            throw new IllegalStateException("fal 응답에 이미지가 없습니다: " + snippet(resp.toString()));
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(120))
                    .GET()
                    .build();
            HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() != 200) {
                throw new IllegalStateException("fal 이미지 다운로드 실패 HTTP " + res.statusCode());
            }
            log.info("💰 fal 이미지 생성 1건 (서버 누적 {}건, 모델={})", imageGenCount.incrementAndGet(), editModel);
            return res.body();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("fal 이미지 다운로드 오류: " + e.getMessage(), e);
        }
    }

    private void sleepMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private String snippet(String s) {
        return s == null ? "" : s.substring(0, Math.min(300, s.length()));
    }
}
