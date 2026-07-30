package com.storyai.backend.ai.image;

import com.storyai.backend.ai.GeminiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Gemini(나노바나나) 삽화 생성기 — 기본 엔진.
 * storyai.ai.image-provider=fal 이면 이 빈 대신 {@link FalImageGenerator}가 활성화된다.
 * 프롬프트는 {@link ImagePrompts}를 공용으로 써서 엔진 간 A/B가 공정하게 되도록 한다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storyai.ai.image-provider", havingValue = "gemini", matchIfMissing = true)
public class GeminiImageGenerator implements ImageGenerator {

    private final GeminiClient gemini;

    @Override
    public boolean isAvailable() {
        return gemini.isConfigured();
    }

    @Override
    public byte[] everydaySheet(List<byte[]> photos, String name, String style, boolean adult) {
        return gemini.generateImage(ImagePrompts.everydaySheet(name, style, adult), photos);
    }

    @Override
    public byte[] costumeSheet(byte[] everydaySheet, String costume, String style, boolean adult) {
        return gemini.generateImage(ImagePrompts.costumeSheet(costume, style, adult), List.of(everydaySheet));
    }

    @Override
    public byte[] mascotSheet(String appearance, String style) {
        return gemini.generateImage(ImagePrompts.mascotSheet(appearance, style), List.of());
    }

    @Override
    public byte[] styleSample(String style) {
        return gemini.generateImage(ImagePrompts.styleSample(style), List.of(), "1:1");
    }

    @Override
    public byte[] illustrate(String scene, List<byte[]> characterSheets, String style) {
        return illustrateWithCompanion(scene, characterSheets, null, null, style);
    }

    @Override
    public byte[] illustrateWithCompanion(String scene, List<byte[]> characterSheets,
                                          byte[] companionSheet, String companionDesc, String style) {
        int n = characterSheets.size();
        List<byte[]> refs = characterSheets;
        if (companionSheet != null) {
            refs = new ArrayList<>(characterSheets);
            refs.add(companionSheet);
        }
        // 세로형 책(185×255)에 맞게 세로 비율(3:4)로 생성 → 세로 페이지에 크롭 없이 꽉 찬다.
        String prompt = ImagePrompts.illustrate(
                scene, n, companionSheet != null ? companionDesc : null, style);
        // 세로형 책(185×255, 3:4에 가까움)에 맞춰 세로 비율로 생성 → 세로 페이지에 크롭 없이 꽉 참.
        return gemini.generateImage(prompt, refs, "3:4");
    }
}
