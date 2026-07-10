package com.storyai.backend.ai.image;

import com.storyai.backend.domain.storycharacter.CharacterRole;

import java.util.List;

/**
 * 삽화 생성기. 벤더 교체가 쉽도록 인터페이스로 추상화. 현재 구현: Gemini(나노바나나).
 */
public interface ImageGenerator {

    boolean isAvailable();

    /** 아이 사진 여러 장 → 파스텔 캐릭터 시트(참조 이미지) PNG 바이트. */
    byte[] characterSheet(List<byte[]> photos, CharacterRole role, String name);

    /** 장면 설명 + 캐릭터 시트(들) → 페이지 삽화 PNG 바이트. */
    byte[] illustrate(String scene, List<byte[]> characterSheets);
}
