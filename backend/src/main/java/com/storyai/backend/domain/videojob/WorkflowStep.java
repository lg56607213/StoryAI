package com.storyai.backend.domain.videojob;

import java.util.Optional;

/**
 * 파이프라인 단계 집합. 실제 진행 순서는 outputType(책/영상)에 따라 WorkflowPlan이 정의한다.
 * - 공통: STORY_GENERATION, CHARACTER_ANALYSIS
 * - 영상: SCENE_SPLIT -> IMAGE_GENERATION -> VIDEO_GENERATION -> VOICE_GENERATION -> SUBTITLE_GENERATION -> VIDEO_COMPOSITION
 * - 책:   PAGE_PLANNING -> PAGE_ILLUSTRATION -> PDF_GENERATION
 */
public enum WorkflowStep {
    STORY_GENERATION,
    SCENE_SPLIT,
    CHARACTER_ANALYSIS,
    IMAGE_GENERATION,
    VIDEO_GENERATION,
    VOICE_GENERATION,
    SUBTITLE_GENERATION,
    VIDEO_COMPOSITION,

    // 책(BOOK) 전용 단계
    PAGE_PLANNING,
    PAGE_ILLUSTRATION,
    PDF_GENERATION;

    public static WorkflowStep first() {
        return STORY_GENERATION;
    }

    public Optional<WorkflowStep> next() {
        WorkflowStep[] all = values();
        int nextOrdinal = this.ordinal() + 1;
        return nextOrdinal < all.length ? Optional.of(all[nextOrdinal]) : Optional.empty();
    }
}
