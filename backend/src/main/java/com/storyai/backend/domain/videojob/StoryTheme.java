package com.storyai.backend.domain.videojob;

import com.storyai.backend.domain.storycharacter.CharacterRole;

/** 스토리 주제. costumeFor()는 전환 이후 아이가 입는 주제별 특별 의상 설명. */
public enum StoryTheme {
    PRINCESS("공주"),
    PRINCE("왕자"),
    HERO("용사"),
    ADVENTURE("모험"),
    ANIMAL_FRIENDS("동물 친구"),
    SPACE("우주 탐험"),
    DINOSAUR("공룡 나라"),
    OCEAN("바닷속"),
    FOREST("숲속 친구들"),
    JUNGLE("정글 탐험"),
    PIRATE("해적 모험");

    private final String label;

    StoryTheme(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 주제에 어울리는 특별 의상(전환 후). PRINCESS/PRINCE만 역할(성별)에 따라 달라진다. */
    public String costumeFor(CharacterRole role) {
        boolean main = role == CharacterRole.MAIN;
        return switch (this) {
            case PRINCESS -> main ? "a pretty pastel princess dress with a small tiara and a floral headband"
                    : "a cute little-prince royal outfit with a small crown";
            case PRINCE -> "a cute little-prince royal outfit with a cape and a small crown";
            case HERO -> "a brave little hero costume with a small cape";
            case ADVENTURE -> "a cute explorer outfit with an adventure vest, hat and a small backpack";
            case ANIMAL_FRIENDS -> "a cozy animal-eared hoodie costume";
            case SPACE -> "a cute little astronaut space suit";
            case DINOSAUR -> "a fun dinosaur explorer outfit with a dino-hood hat and an adventure vest";
            case OCEAN -> "a cute diver or sailor outfit with a life vest";
            case FOREST -> "a woodland ranger outfit with a leafy cape";
            case JUNGLE -> "a safari explorer outfit with a hat and binoculars";
            case PIRATE -> "a friendly little pirate outfit with a bandana and a toy sword";
        };
    }
}
