package com.storyai.backend.domain.videojob;

import java.util.Optional;

/**
 * Pipeline order is defined by enum declaration order:
 * 입력 -> 스토리생성 -> 장면분리 -> 캐릭터분석 -> 이미지생성 -> 영상생성 -> 음성생성 -> 자막생성 -> 영상합성
 */
public enum WorkflowStep {
    STORY_GENERATION,
    SCENE_SPLIT,
    CHARACTER_ANALYSIS,
    IMAGE_GENERATION,
    VIDEO_GENERATION,
    VOICE_GENERATION,
    SUBTITLE_GENERATION,
    VIDEO_COMPOSITION;

    public static WorkflowStep first() {
        return values()[0];
    }

    public Optional<WorkflowStep> next() {
        WorkflowStep[] all = values();
        int nextOrdinal = this.ordinal() + 1;
        return nextOrdinal < all.length ? Optional.of(all[nextOrdinal]) : Optional.empty();
    }
}
