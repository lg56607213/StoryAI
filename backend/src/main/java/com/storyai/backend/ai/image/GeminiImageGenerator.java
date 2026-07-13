package com.storyai.backend.ai.image;

import com.storyai.backend.ai.GeminiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GeminiImageGenerator implements ImageGenerator {

    /** style이 비어있을 때 쓰는 기본 화풍(동화형식). */
    private static final String DEFAULT_STYLE =
            "soft warm colored-pencil and watercolor Korean children's picture-book illustration; "
                    + "warm golden-cream lighting with a gentle glow; semi-realistic soft faces";

    private final GeminiClient gemini;

    @Override
    public boolean isAvailable() {
        return gemini.isConfigured();
    }

    private String styleLine(String style) {
        return "Art style: " + (style == null || style.isBlank() ? DEFAULT_STYLE : style)
                + " (realistic proportions, NOT chibi).";
    }

    @Override
    public byte[] everydaySheet(List<byte[]> photos, String name, String style) {
        String prompt = "You are given several photos of the SAME real young child"
                + (name != null && !name.isBlank() ? " (name: " + name + ")" : "")
                + ". Create ONE full-body character reference portrait that strongly and recognizably looks like the "
                + "SAME specific child (same face, cheeks, eyes, smile, hairstyle). CRUCIAL: keep the child's REAL "
                + "everyday clothing/outfit exactly as worn in the photos - the same top/T-shirt, its colors and "
                + "pattern - do NOT put the child in any costume. " + styleLine(style)
                + " Standing, gentle happy smile, plain warm cream background, centered. No text, no watermark.";
        return gemini.generateImage(prompt, photos);
    }

    @Override
    public byte[] costumeSheet(byte[] everydaySheet, String costume, String style) {
        String prompt = "This reference image is a young child storybook character. Keep the EXACT same face, "
                + "likeness, cheeks, eyes, smile and hairstyle identical, but change the clothing to " + costume + ". "
                + styleLine(style) + " Full body, standing, plain warm cream background, centered. No text, no watermark.";
        return gemini.generateImage(prompt, List.of(everydaySheet));
    }

    @Override
    public byte[] illustrate(String scene, List<byte[]> characterSheets, String style) {
        StringBuilder refInfo = new StringBuilder();
        for (int i = 0; i < characterSheets.size(); i++) {
            refInfo.append("Reference image ").append(i + 1)
                    .append(" is a main character - keep this character IDENTICAL (same face, likeness, hairstyle, "
                            + "and the exact outfit shown in the reference). ");
        }
        String prompt = styleLine(style) + " Create ONE wide children's storybook illustration for this scene: "
                + scene + ". " + refInfo
                + "Only include a character if the scene calls for them. Full scene with background, warm and tender mood. "
                + "Fill the entire wide frame edge-to-edge with the scene (full background, no empty margins). "
                + "IMPORTANT: no text, no words, no letters, no watermark in the image.";
        // 가로형 책에 맞춰 landscape(3:2)로 생성 → 페이지를 여백 없이 꽉 채움.
        return gemini.generateImage(prompt, characterSheets, "3:2");
    }
}
