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

    /** 해부학 오류(팔·다리·손가락 개수 이상) 방지 지시문. */
    private static final String ANATOMY =
            " Draw natural, correct human anatomy: each person has EXACTLY two arms, two legs, one head, and five "
                    + "fingers per hand — no extra, missing, duplicated, or fused limbs, and no deformed hands or faces.";

    private final GeminiClient gemini;

    @Override
    public boolean isAvailable() {
        return gemini.isConfigured();
    }

    private String styleLine(String style) {
        String s = (style == null || style.isBlank()) ? DEFAULT_STYLE : style;
        // 참조 이미지의 렌더링이 화풍을 눌러버리지 않도록, 선택한 화풍을 강하게 강제한다.
        return "IMPORTANT — render the WHOLE image strictly in this exact art style, applied consistently to "
                + "characters and background: " + s + ". Do not default to a generic look; commit fully to this style "
                + "(realistic proportions, NOT chibi).";
    }

    @Override
    public byte[] everydaySheet(List<byte[]> photos, String name, String style) {
        String prompt = "You are given photos of the SAME real young child"
                + (name != null && !name.isBlank() ? " (name: " + name + ")" : "")
                + ". Create ONE full-body character reference portrait that looks STRONGLY and recognizably like the "
                + "SAME specific child - keep the exact face shape, round cheeks, eyes, eyebrows, nose, mouth and smile, "
                + "skin tone, and hairstyle (including any hairband or hair accessory). "
                + "CRUCIAL: keep the child's REAL everyday outfit exactly as worn in the photos - the same top, its colors, "
                + "pattern and sleeves - do NOT change, simplify, or invent clothing, and do NOT put the child in a costume. "
                + styleLine(style) + ANATOMY
                + " Standing, facing forward, gentle happy smile, plain warm cream background, centered. No text, no watermark.";
        return gemini.generateImage(prompt, photos);
    }

    @Override
    public byte[] costumeSheet(byte[] everydaySheet, String costume, String style) {
        String prompt = "This reference image is a young child storybook character. Keep the EXACT same face, "
                + "likeness, cheeks, eyes, smile and hairstyle identical, but change the clothing to " + costume + ". "
                + "Keep the outfit appropriate and natural for THIS specific child as shown in the reference. "
                + styleLine(style) + ANATOMY
                + " Full body, standing, plain warm cream background, centered. No text, no watermark.";
        return gemini.generateImage(prompt, List.of(everydaySheet));
    }

    @Override
    public byte[] illustrate(String scene, List<byte[]> characterSheets, String style) {
        int n = characterSheets.size();
        StringBuilder refInfo = new StringBuilder();
        for (int i = 0; i < n; i++) {
            refInfo.append("Reference image ").append(i + 1)
                    .append(" is a main character - keep this character IDENTICAL (same face, likeness, hairstyle, "
                            + "and the exact outfit shown in the reference). ");
        }
        String peopleRule = "This whole story has EXACTLY " + n + " human character" + (n == 1 ? "" : "s")
                + ". Draw ONLY the " + n + " reference character" + (n == 1 ? "" : "s") + " above. "
                + "Do NOT add, invent, duplicate, or draw ANY other person, child, friend, sibling, or bystander "
                + "that is not one of the references - even if the scene text seems to mention someone else. ";
        String prompt = styleLine(style) + " Create ONE wide children's storybook illustration for this scene: "
                + scene + ". " + refInfo + peopleRule
                + "Keep each character's clothing EXACTLY the same as in their reference image — do not change, swap, "
                + "or invent outfits between pages." + ANATOMY + " "
                + "Full scene with background, warm and tender mood. "
                + "Fill the entire wide frame edge-to-edge with the scene (full background, no empty margins). "
                + "IMPORTANT: no text, no words, no letters, no watermark in the image.";
        // 가로형 책에 맞춰 landscape(3:2)로 생성 → 페이지를 여백 없이 꽉 채움.
        return gemini.generateImage(prompt, characterSheets, "3:2");
    }
}
