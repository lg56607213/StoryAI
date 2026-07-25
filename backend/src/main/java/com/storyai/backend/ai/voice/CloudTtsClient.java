package com.storyai.backend.ai.voice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.Map;

/**
 * Google Cloud Text-to-Speech(정식 TTS) 클라이언트.
 * Gemini 프리뷰 TTS는 한도(rate limit)가 낮아 긴 영상에서 대부분 실패 → 정식 Cloud TTS로 대사·서술을 합성한다.
 * 결제 계정은 Gemini와 공유되므로 같은 API 키(GEMINI_API_KEY)로 호출한다(프로젝트에 Cloud TTS API 사용설정 필요).
 */
@Slf4j
@Component
public class CloudTtsClient {

    private static final String URL = "https://texttospeech.googleapis.com/v1/text:synthesize";
    private static final int SAMPLE_RATE = 24000;

    private final String apiKey;
    private final boolean enabled;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    /** 앱 목소리 코드 → (ko-KR 음성, 피치 반음, 말속도). Neural2는 피치·속도 조절 지원(어린이·동물 느낌). */
    private record Voice(String name, double pitch, double rate) {
    }

    private static final Map<String, Voice> VOICES = Map.of(
            "narrator", new Voice("ko-KR-Neural2-A", 0.0, 0.98),   // 따뜻한 여성 서술
            "hero", new Voice("ko-KR-Neural2-A", 3.0, 1.03),        // 주인공(밝은 아이 톤)
            "child_girl", new Voice("ko-KR-Neural2-B", 4.0, 1.05),
            "child_boy", new Voice("ko-KR-Neural2-C", 4.0, 1.05),
            "small_animal", new Voice("ko-KR-Neural2-A", 6.0, 1.08),
            "big_animal", new Voice("ko-KR-Neural2-C", -5.0, 0.92),
            "elder", new Voice("ko-KR-Neural2-C", -2.0, 0.90),
            "fairy", new Voice("ko-KR-Neural2-B", 2.0, 1.0)
    );

    public CloudTtsClient(@Value("${storyai.ai.cloud-tts.api-key:${storyai.ai.gemini.api-key:}}") String apiKey,
                          @Value("${storyai.ai.cloud-tts.enabled:true}") boolean enabled,
                          ObjectMapper mapper) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.enabled = enabled;
        this.mapper = mapper;
    }

    public boolean isConfigured() {
        return enabled && !apiKey.isBlank();
    }

    /**
     * 텍스트를 지정 목소리 코드로 합성해 WAV 바이트로 돌려준다(24kHz mono, 44바이트 헤더).
     * voiceCode가 매핑에 없으면 narrator로 대체한다.
     */
    public byte[] synthesize(String text, String voiceCode) {
        Voice v = VOICES.getOrDefault(voiceCode == null ? "narrator" : voiceCode, VOICES.get("narrator"));

        ObjectNode body = mapper.createObjectNode();
        body.putObject("input").put("text", text);
        ObjectNode voice = body.putObject("voice");
        voice.put("languageCode", "ko-KR");
        voice.put("name", v.name());
        ObjectNode audio = body.putObject("audioConfig");
        audio.put("audioEncoding", "LINEAR16");
        audio.put("sampleRateHertz", SAMPLE_RATE);
        audio.put("pitch", v.pitch());
        audio.put("speakingRate", v.rate());

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(URL + "?key=" + apiKey))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() != 200) {
                throw new IllegalStateException("Cloud TTS HTTP " + res.statusCode() + ": "
                        + trim(res.body()));
            }
            JsonNode node = mapper.readTree(res.body());
            String b64 = node.path("audioContent").asText("");
            if (b64.isEmpty()) {
                throw new IllegalStateException("Cloud TTS 응답에 audioContent 없음");
            }
            return Base64.getDecoder().decode(b64); // LINEAR16 = WAV(헤더 포함)
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Cloud TTS 호출 실패: " + e.getMessage(), e);
        }
    }

    private String trim(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 400 ? s.substring(0, 400) : s;
    }
}
