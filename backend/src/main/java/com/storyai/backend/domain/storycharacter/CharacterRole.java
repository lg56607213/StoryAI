package com.storyai.backend.domain.storycharacter;

/**
 * 스토리에 등장하는 입력 인물의 역할(주인공 기준의 관계). 첫 인물은 항상 MAIN.
 * CUSTOM은 고객이 직접 입력한 관계를 쓰며, 실제 표기는 StoryCharacter.customRole을 따른다.
 * SIBLING은 구버전 데이터 호환용으로 남겨둔다.
 */
public enum CharacterRole {
    MAIN("주인공"),
    MOM("엄마"),
    DAD("아빠"),
    OLDER_BROTHER("형/오빠"),
    OLDER_SISTER("누나/언니"),
    YOUNGER_SIBLING("동생"),
    FRIEND("친구"),
    CUSTOM("직접입력"),
    /** @deprecated 구버전 선택지. 신규 생성에는 쓰지 않는다. */
    @Deprecated
    SIBLING("형제/자매");

    private final String label;

    CharacterRole(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 어른(보호자) 역할인지 — 삽화에서 아이로 그리지 않도록 구분한다. */
    public boolean isAdult() {
        return this == MOM || this == DAD;
    }
}
