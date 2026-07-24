package com.storyai.backend.ai.image;

/**
 * 삽화 생성 프롬프트 단일 소스. Gemini·fal 등 어떤 엔진을 쓰든 동일한 프롬프트를 사용해
 * 엔진 교체(A/B) 시 "모델 차이"만 비교되도록 한다.
 */
public final class ImagePrompts {

    private ImagePrompts() {
    }

    /** style이 비어있을 때 쓰는 기본 화풍(동화형식). */
    public static final String DEFAULT_STYLE =
            "soft warm colored-pencil and watercolor Korean children's picture-book illustration; "
                    + "warm golden-cream lighting with a gentle glow; semi-realistic soft faces";

    /** 해부학 오류(팔·다리·손가락 개수 이상) 방지 지시문. */
    public static final String ANATOMY =
            " Draw natural, correct human anatomy: each person has EXACTLY two arms, two legs, one head, and five "
                    + "fingers per hand — no extra, missing, duplicated, or fused limbs, and no deformed hands or faces.";

    /** 참조 이미지의 렌더링이 화풍을 눌러버리지 않도록, 선택한 화풍을 강하게 강제한다. */
    public static String styleLine(String style) {
        String s = (style == null || style.isBlank()) ? DEFAULT_STYLE : style;
        return "IMPORTANT — render the WHOLE image strictly in this exact art style, applied consistently to "
                + "characters and background: " + s + ". Do not default to a generic look; commit fully to this style "
                + "(realistic proportions, NOT chibi).";
    }

    /**
     * 사진(들) → 실제 옷을 그대로 살린 평상복 캐릭터 시트.
     * adult=true면 어른(엄마·아빠 등)으로 그린다 — 아이로 그리지 않도록 구분한다.
     */
    public static String everydaySheet(String name, String style, boolean adult) {
        String subject = adult ? "adult person" : "young child";
        return "You are given photos of the SAME real " + subject
                + (name != null && !name.isBlank() ? " (name: " + name + ")" : "")
                + ". Create ONE full-body character reference portrait that looks STRONGLY and recognizably like the "
                + "SAME specific person - keep the exact face shape, cheeks, eyes, eyebrows, nose, mouth and smile, "
                + "skin tone, and hairstyle (including any hairband or hair accessory). "
                + (adult
                    ? "Draw them clearly as a GROWN-UP adult with adult body proportions - never as a child. "
                    : "Draw them clearly as a young child with age-appropriate child proportions. ")
                + "CRUCIAL: keep the REAL everyday outfit exactly as worn in the photos - the same top, its colors, "
                + "pattern and sleeves - do NOT change, simplify, or invent clothing, and do NOT put them in a costume. "
                + styleLine(style) + ANATOMY
                + " Standing, facing forward, gentle happy smile, plain warm cream background, centered. No text, no watermark.";
    }

    /** 평상복 시트(얼굴 고정) → 주제 의상 시트. */
    public static String costumeSheet(String costume, String style, boolean adult) {
        return "This reference image is " + (adult ? "an adult" : "a young child")
                + " storybook character. Keep the EXACT same face, "
                + "likeness, cheeks, eyes, smile and hairstyle identical, but change the clothing to " + costume + ". "
                + "Keep the outfit appropriate and natural for THIS specific person as shown in the reference, "
                + "and keep their age unchanged (" + (adult ? "still a grown-up adult" : "still a young child") + "). "
                + styleLine(style) + ANATOMY
                + " Full body, standing, plain warm cream background, centered. No text, no watermark.";
    }

    /** 마스코트("히어로 친구들") 시트 — 참조 이미지 없이 외형 스펙만으로 생성. */
    public static String mascotSheet(String appearance, String style) {
        return styleLine(style)
                + " Create ONE full-body character reference illustration of an original children's-book "
                + "animal mascot: " + appearance + ". "
                + "Friendly, cute and huggable, gentle happy expression, standing/floating and facing forward, "
                + "centered on a plain warm cream background. "
                + "Keep the design simple and iconic so it can be redrawn identically many times. "
                + "No text, no words, no letters, no watermark.";
    }

    /**
     * 페이지 삽화 프롬프트.
     * @param humanCount 사람 주인공 수(참조 이미지 1..humanCount)
     * @param companionDesc 동물 동반자 설명(없으면 null) — 사람 수에 포함하지 않는다.
     */
    public static String illustrate(String scene, int humanCount, String companionDesc, String style) {
        StringBuilder refInfo = new StringBuilder();
        for (int i = 0; i < humanCount; i++) {
            refInfo.append("Reference image ").append(i + 1)
                    .append(" is a main character - keep this character's IDENTITY identical (same face, likeness, "
                            + "hairstyle, and the exact outfit shown in the reference). "
                            + "The reference is only a neutral standing pose for identity: do NOT copy that pose. "
                            + "Re-pose this character freely to act out the scene described below. ");
        }
        if (companionDesc != null) {
            refInfo.append("Reference image ").append(humanCount + 1)
                    .append(" is ").append(companionDesc)
                    .append(" - an ANIMAL companion, not a person. Keep this creature IDENTICAL to its reference "
                            + "(same species, colors, markings, proportions and accessories). ");
        }
        String peopleRule = "This whole story has EXACTLY " + humanCount + " human character"
                + (humanCount == 1 ? "" : "s")
                + ". Draw ONLY the " + humanCount + " human reference character" + (humanCount == 1 ? "" : "s")
                + " above" + (companionDesc != null ? " plus the animal companion" : "") + ". "
                + "Do NOT add, invent, duplicate, or draw ANY other person, child, friend, sibling, or bystander "
                + "that is not one of the references - even if the scene text seems to mention someone else. ";

        // 참조 시트가 "정면·차렷" 자세라 그대로 따라 그리는 경향이 있다 → 표정·동작을 명시적으로 요구한다.
        String expressive =
                "STORYTELLING (most important): this is a narrative moment, not a character portrait. "
                        + "Act out the scene: give each character a CLEAR facial expression that matches the "
                        + "emotion in the scene (crying, laughing, surprised, worried, proud, shy) and an "
                        + "EXPRESSIVE full-body pose and gesture (kneeling, hugging, reaching out, tumbling, "
                        + "hiding, pointing). Show how the characters relate to each other in the moment. "
                        + "Frame them as a close or medium shot so faces and feelings read clearly. "
                        + "Do NOT draw characters standing straight and still facing the camera. ";

        return styleLine(style) + " Create ONE wide children's storybook illustration for this scene: "
                + scene + ". " + refInfo + peopleRule + expressive
                + "Keep each character's clothing EXACTLY the same as in their reference image — do not change, swap, "
                + "or invent outfits between pages." + ANATOMY + " "
                + "Full scene with background, warm and tender mood. "
                + "Fill the entire wide frame edge-to-edge with the scene (full background, no empty margins). "
                + "IMPORTANT: no text, no words, no letters, no watermark in the image.";
    }
}
