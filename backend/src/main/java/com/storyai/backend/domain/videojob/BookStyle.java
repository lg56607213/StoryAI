package com.storyai.backend.domain.videojob;

/** 책 산출물의 그림 스타일. guide는 삽화 생성 프롬프트에 들어가는 화풍 지시문. */
public enum BookStyle {
    STORYBOOK("동화형식",
            "soft warm colored-pencil and watercolor children's picture-book illustration; warm golden-cream "
                    + "lighting with a gentle glow and delicate sparkles; fine pencil texture; soft semi-realistic faces"),
    SKETCH("스케치형식",
            "gentle hand-drawn pencil sketch style with soft light coloring; visible sketch linework and cross-hatch "
                    + "shading, soft muted colors, cozy storybook feel"),
    COLORED_PENCIL("색연필",
            "vivid colored-pencil illustration; rich layered pencil strokes on textured paper, bright yet cozy "
                    + "colors, warm children's-book mood"),
    WATERCOLOR("수채화",
            "soft watercolor painting; gentle washes and blended pastel colors, light paper texture, dreamy and "
                    + "tender storybook mood"),
    CRISP_CARTOON("또렷한 그림체",
            "clean crisp cartoon/anime children's-book style with bold clear outlines, flat bright vivid colors and "
                    + "simple shading; clear, lively and easy to read");

    private final String label;
    private final String guide;

    BookStyle(String label, String guide) {
        this.label = label;
        this.guide = guide;
    }

    public String getLabel() {
        return label;
    }

    public String getGuide() {
        return guide;
    }
}
