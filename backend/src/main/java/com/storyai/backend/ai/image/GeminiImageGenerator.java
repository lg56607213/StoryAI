package com.storyai.backend.ai.image;

import com.storyai.backend.ai.GeminiClient;
import com.storyai.backend.domain.storycharacter.CharacterRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GeminiImageGenerator implements ImageGenerator {

    /** PoC에서 확정한 파스텔 동화 화풍(색연필+수채, 골드 글로우, 반실사). */
    private static final String STYLE =
            "Art style: soft warm colored-pencil and watercolor Korean children's picture-book illustration; "
                    + "warm golden-cream lighting with a gentle magical glow and delicate sparkles; "
                    + "dreamy soft pastel palette of creams, soft pinks and warm gold; fine detailed pencil texture; "
                    + "semi-realistic faces with realistic proportions (NOT cartoon, NOT anime, NOT chibi).";

    private final GeminiClient gemini;

    @Override
    public boolean isAvailable() {
        return gemini.isConfigured();
    }

    @Override
    public byte[] characterSheet(List<byte[]> photos, CharacterRole role, String name) {
        String outfit = role == CharacterRole.MAIN
                ? "a soft pink princess dress with a small tiara and a floral headband"
                : "a cute soft pastel little-prince outfit";
        String prompt = "You are given several photos of the SAME real young child"
                + (name != null && !name.isBlank() ? " (name: " + name + ")" : "")
                + ". Carefully study the child's REAL facial features across all photos - face shape, eyes, cheeks, "
                + "nose, mouth, smile, and hairstyle - then create ONE single character reference portrait that "
                + "strongly and recognizably looks like the SAME specific child. " + STYLE
                + " Full body, standing, wearing " + outfit + ", gentle happy smile, plain warm cream studio "
                + "background, centered. No text, no watermark.";
        return gemini.generateImage(prompt, photos);
    }

    @Override
    public byte[] illustrate(String scene, List<byte[]> characterSheets) {
        StringBuilder refInfo = new StringBuilder();
        for (int i = 0; i < characterSheets.size(); i++) {
            refInfo.append("Reference image ").append(i + 1)
                    .append(" is a main character - keep this character IDENTICAL (same face, likeness, hairstyle, outfit). ");
        }
        String prompt = STYLE + " Create ONE wide children's storybook illustration for this scene: " + scene + ". "
                + refInfo
                + "Only include a character if the scene calls for them. Full scene with background, warm and tender mood. "
                + "IMPORTANT: no text, no words, no letters, no watermark in the image.";
        return gemini.generateImage(prompt, characterSheets);
    }
}
