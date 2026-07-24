package com.storyai.backend.health;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class HealthController {

    private final com.storyai.backend.ai.ClaudeClient claudeClient;
    private final com.storyai.backend.ai.GeminiClient geminiClient;
    private final com.storyai.backend.ai.voice.ElevenLabsClient elevenLabsClient;
    private final com.storyai.backend.notify.EmailNotifier emailNotifier;
    private final com.storyai.backend.video.NarrationVideoService narrationVideoService;
    private final com.storyai.backend.ai.image.ImageGenerator imageGenerator;
    private final com.storyai.backend.storage.StorageService storageService;

    /** 배포 확인용 표식. 기능이 바뀔 때마다 갱신해 "새 빌드가 떴는지"를 즉시 판별한다. */
    private static final String BUILD = "2026-07-24-schema-migration";
    /** 서버 기동 시각 — 값이 바뀌면 재시작(재배포)된 것. */
    private static final java.time.Instant STARTED_AT = java.time.Instant.now();

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "service", "storyai-backend");
    }

    /**
     * 연동 준비 상태(공개). 어떤 기능이 켜져 있는지 boolean으로만 노출한다.
     * 비밀키·계정 정보는 절대 포함하지 않으며, 운영 중 "무엇이 안 되는지"를 빠르게 확인하는 용도다.
     */
    @GetMapping("/api/status")
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("build", BUILD);
        m.put("startedAt", STARTED_AT.toString());
        m.put("story", claudeClient.isConfigured() ? "claude" : "gemini");
        m.put("image", imageGenerator.isAvailable());
        m.put("characterVoice", geminiClient.isConfigured());
        m.put("parentVoice", elevenLabsClient.isConfigured());
        m.put("ffmpeg", narrationVideoService.isFfmpegAvailable());
        m.put("email", emailNotifier.isConfigured());
        m.put("storage", storageService.rootPath().startsWith("r2://") ? "r2" : "local");
        m.put("storageWritable", storageService.writable());
        return m;
    }
}
