package com.storyai.backend.ai.voice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * ElevenLabs 클라이언트 — 부모 목소리 복제(Instant Voice Cloning) + 그 목소리로 낭독.
 * 서술(narrator) 세그먼트만 부모 목소리로 읽고, 등장인물 대사는 Gemini 목소리를 그대로 쓴다.
 *
 * 출력은 PCM 24kHz 16-bit로 받아 Gemini TTS 파이프라인(WavAudio)과 동일하게 이어붙인다.
 * 키(ELEVENLABS_API_KEY) 미설정 시 isConfigured()=false → 호출부가 Gemini 내레이터로 폴백.
 */
@Slf4j
@Component
public class ElevenLabsClient {

    private static final String BASE = "https://api.elevenlabs.io/v1";
    /** Gemini TTS와 동일한 샘플레이트로 받아 오디오 이어붙이기를 단순화한다. */
    public static final int SAMPLE_RATE = 24000;

    private final String apiKey;
    private final String model;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public ElevenLabsClient(@Value("${storyai.ai.elevenlabs-api-key:}") String apiKey,
                            @Value("${storyai.ai.elevenlabs.model:eleven_multilingual_v2}") String model,
                            ObjectMapper mapper) {
        this.apiKey = apiKey;
        this.model = model;
        this.mapper = mapper;
    }

    @PostConstruct
    void logConfig() {
        log.info("ElevenLabsClient 설정: configured={}, model={}", isConfigured(), model);
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * 부모 목소리 샘플(녹음 파일)로 음성을 복제하고 voiceId를 돌려준다.
     * @param name 식별용 이름(예: "todayhero-job-123")
     * @param audio 녹음 바이트(webm/mp3/wav 등)
     * @param filename 확장자가 포함된 파일명(형식 추론용)
     */
    public String cloneVoice(String name, byte[] audio, String filename) {
        requireConfigured();
        String boundary = "----todayhero" + UUID.randomUUID().toString().replace("-", "");
        byte[] body = multipart(boundary, name, audio, filename);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/voices/add"))
                .timeout(Duration.ofSeconds(180))
                .header("xi-api-key", apiKey)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() != 200) {
                throw new IllegalStateException("ElevenLabs 음성 복제 실패 HTTP " + res.statusCode() + ": "
                        + snippet(res.body()));
            }
            JsonNode json = mapper.readTree(res.body());
            String voiceId = json.path("voice_id").asText("");
            if (voiceId.isBlank()) {
                throw new IllegalStateException("ElevenLabs 응답에 voice_id 없음: " + snippet(res.body()));
            }
            return voiceId;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("ElevenLabs 음성 복제 오류: " + e.getMessage(), e);
        }
    }

    /** 복제된 목소리로 텍스트를 읽어 raw PCM(24kHz, 16-bit, mono) 바이트를 반환. */
    public byte[] textToSpeechPcm(String text, String voiceId) {
        requireConfigured();
        ObjectNode body = mapper.createObjectNode();
        body.put("text", text);
        body.put("model_id", model);

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/text-to-speech/" + voiceId + "?output_format=pcm_" + SAMPLE_RATE))
                    .timeout(Duration.ofSeconds(180))
                    .header("xi-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() != 200) {
                throw new IllegalStateException("ElevenLabs 합성 실패 HTTP " + res.statusCode() + ": "
                        + snippet(new String(res.body(), StandardCharsets.UTF_8)));
            }
            return res.body();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("ElevenLabs 합성 오류: " + e.getMessage(), e);
        }
    }

    /** 복제 음성 삭제(개인정보 보호 — 영상 생성 후 정리). 실패해도 흐름을 막지 않는다. */
    public void deleteVoice(String voiceId) {
        if (!isConfigured() || voiceId == null || voiceId.isBlank()) {
            return;
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/voices/" + voiceId))
                    .timeout(Duration.ofSeconds(30))
                    .header("xi-api-key", apiKey)
                    .DELETE()
                    .build();
            http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.warn("ElevenLabs 음성 삭제 실패({}): {}", voiceId, e.getMessage());
        }
    }

    // ---------- 내부 ----------

    private byte[] multipart(String boundary, String name, byte[] audio, String filename) {
        String safeName = (filename == null || filename.isBlank()) ? "sample.webm" : filename;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write("Content-Disposition: form-data; name=\"name\"\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            out.write((name + "\r\n").getBytes(StandardCharsets.UTF_8));

            out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"files\"; filename=\"" + safeName + "\"\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Type: " + contentTypeOf(safeName) + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(audio);
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));

            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("multipart 생성 실패: " + e.getMessage(), e);
        }
        return out.toByteArray();
    }

    private String contentTypeOf(String filename) {
        String f = filename.toLowerCase();
        if (f.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        if (f.endsWith(".wav")) {
            return "audio/wav";
        }
        if (f.endsWith(".m4a") || f.endsWith(".mp4")) {
            return "audio/mp4";
        }
        if (f.endsWith(".ogg")) {
            return "audio/ogg";
        }
        return "audio/webm";
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("ELEVENLABS_API_KEY가 설정되지 않았습니다.");
        }
    }

    private String snippet(String s) {
        return s == null ? "" : s.substring(0, Math.min(300, s.length()));
    }
}
