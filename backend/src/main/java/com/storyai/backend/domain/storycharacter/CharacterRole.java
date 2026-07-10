package com.storyai.backend.domain.storycharacter;

/** 스토리에 등장하는 입력 인물의 역할. 첫 인물은 항상 MAIN. */
public enum CharacterRole {
    MAIN("주인공"),
    SIBLING("형제/자매"),
    FRIEND("친구");

    private final String label;

    CharacterRole(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
