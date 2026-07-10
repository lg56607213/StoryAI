package com.storyai.backend.domain.videojob;

/** 스토리 주제. 공주/왕자/용사 등 유아가 좋아하는 역할 중심. */
public enum StoryTheme {
    PRINCESS("공주"),
    PRINCE("왕자"),
    HERO("용사"),
    ADVENTURE("모험"),
    ANIMAL_FRIENDS("동물 친구"),
    SPACE("우주 탐험");

    private final String label;

    StoryTheme(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
