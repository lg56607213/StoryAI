package com.storyai.backend.ai.story;

/**
 * 책 한 페이지 초안.
 * - text: 페이지에 인쇄될 한국어 이야기 문구
 * - scene: 삽화 생성용 장면 설명(영문, 등장인물/배경/동작/의상 포함)
 * - outfit: "everyday"(실제 옷) 또는 "costume"(주제 의상)
 */
public record BookPageDraft(String text, String scene, String outfit) {
}
