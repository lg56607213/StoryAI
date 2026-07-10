package com.storyai.backend.domain.videojob;

/** 책 산출물의 그림 스타일. */
public enum BookStyle {
    STORYBOOK("동화형식"),
    SKETCH("스케치형식");

    private final String label;

    BookStyle(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
