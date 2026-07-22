package com.storyai.backend.ai.story;

/**
 * 페이지 본문을 낭독용으로 쪼갠 한 조각(서술 또는 대사).
 * 영상 낭독에서 세그먼트마다 다른 목소리로 읽어준다.
 * - role   : "narration"(서술) 또는 "dialogue"(대사)
 * - speaker: 대사를 말하는 등장인물 이름(예: "토끼", "소영"). 서술이면 빈 문자열.
 * - voice  : 목소리 캐스팅 힌트. {@link VoiceCast} 값의 소문자 문자열(narrator/hero/small_animal 등).
 * - text   : 실제 인쇄·낭독될 문구.
 */
public record NarrationSegment(String role, String speaker, String voice, String text) {
}
