package com.storyai.backend.ai.image;

import java.util.List;

/**
 * 삽화 생성기. 벤더 교체가 쉽도록 인터페이스로 추상화. 현재 구현: Gemini(나노바나나).
 * style: 선택한 그림 스타일(BookStyle.getGuide()) 지시문.
 */
public interface ImageGenerator {

    boolean isAvailable();

    /**
     * 사진 여러 장 → 실제 옷을 그대로 살린 "평상복" 캐릭터 시트 PNG (본인 인식용).
     * adult=true면 어른(엄마·아빠 등)으로 그린다.
     */
    byte[] everydaySheet(List<byte[]> photos, String name, String style, boolean adult);

    /** 평상복 시트(얼굴 고정) → 주제 의상으로 갈아입힌 "주제 의상" 시트 PNG. */
    byte[] costumeSheet(byte[] everydaySheet, String costume, String style, boolean adult);

    /** 장면 설명 + 캐릭터 시트(들) → 페이지 삽화 PNG 바이트. */
    byte[] illustrate(String scene, List<byte[]> characterSheets, String style);

    /**
     * 마스코트("히어로 친구들") 캐릭터 시트 PNG. 사진 참조 없이 외형 스펙만으로 생성한다.
     * (캐릭터×화풍 조합당 1회만 생성해 캐시 → 모든 책에서 동일한 모습)
     */
    byte[] mascotSheet(String appearance, String style);

    /**
     * 마스코트를 동반자로 함께 그리는 삽화.
     * characterSheets = 사람 주인공 시트(사람 수 규칙에 사용), companionSheet = 동물 친구 시트(사람으로 세지 않음).
     */
    byte[] illustrateWithCompanion(String scene, List<byte[]> characterSheets,
                                   byte[] companionSheet, String companionDesc, String style);
}
