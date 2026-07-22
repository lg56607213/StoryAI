package com.storyai.backend.ai.story;

/**
 * 낭독 목소리 캐스팅 힌트. 스토리 생성기가 각 대사/서술에 이 값 중 하나를 붙이고,
 * TTS 단계(캐스팅 레이어)에서 실제 음성(부모 목소리·Google 목소리 등)으로 매핑한다.
 * 값이 이상하면 NARRATOR로 안전 폴백한다.
 */
public enum VoiceCast {
    /** 이야기 서술 → 부모 목소리(없으면 기본 내레이터). */
    NARRATOR,
    /** 주인공 대사 → 아이 성별에 맞춰 캐스팅. */
    HERO,
    /** 여자아이 조연. */
    CHILD_GIRL,
    /** 남자아이 조연. */
    CHILD_BOY,
    /** 작고 귀여운 동물(토끼·다람쥐·아기새) → 밝고 높은 아이 목소리. */
    SMALL_ANIMAL,
    /** 크고 힘센 동물(사자·곰·호랑이) → 굵고 낮은 목소리. */
    BIG_ANIMAL,
    /** 지혜롭거나 나이 든 존재(부엉이·할아버지나무) → 차분한 어른 목소리. */
    ELDER,
    /** 요정·마법 존재 → 맑은 여성 목소리. */
    FAIRY;

    public static VoiceCast from(String s) {
        if (s == null || s.isBlank()) {
            return NARRATOR;
        }
        try {
            return VoiceCast.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NARRATOR;
        }
    }
}
