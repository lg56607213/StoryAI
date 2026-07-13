package com.storyai.backend.ai.image;

import java.util.List;

/**
 * 삽화 생성기. 벤더 교체가 쉽도록 인터페이스로 추상화. 현재 구현: Gemini(나노바나나).
 * style: 선택한 그림 스타일(BookStyle.getGuide()) 지시문.
 */
public interface ImageGenerator {

    boolean isAvailable();

    /** 아이 사진 여러 장 → 실제 옷을 그대로 살린 "평상복" 캐릭터 시트 PNG (본인 인식용). */
    byte[] everydaySheet(List<byte[]> photos, String name, String style);

    /** 평상복 시트(얼굴 고정) → 주제 의상으로 갈아입힌 "주제 의상" 시트 PNG. */
    byte[] costumeSheet(byte[] everydaySheet, String costume, String style);

    /** 장면 설명 + 캐릭터 시트(들) → 페이지 삽화 PNG 바이트. */
    byte[] illustrate(String scene, List<byte[]> characterSheets, String style);
}
