package com.storyai.backend.ai.story;

import java.util.List;

/**
 * 책 한 페이지 초안.
 * - text: 페이지에 인쇄될 한국어 이야기 문구(세그먼트가 있으면 세그먼트 텍스트를 이어 붙인 것)
 * - scene: 삽화 생성용 장면 설명(영문, 등장인물/배경/동작/의상 포함)
 * - outfit: "everyday"(실제 옷) 또는 "costume"(주제 의상)
 * - segments: 낭독용 조각(서술/대사 + 화자 + 목소리 힌트). 영상 낭독에서 목소리를 나눠 읽는 데 쓴다.
 */
public record BookPageDraft(String text, String scene, String outfit, List<NarrationSegment> segments) {

    /** 세그먼트 없이 만드는 하위호환 생성자(더미 폴백 등에서 사용). */
    public BookPageDraft(String text, String scene, String outfit) {
        this(text, scene, outfit, List.of());
    }
}
