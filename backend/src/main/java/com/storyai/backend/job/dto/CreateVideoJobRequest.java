package com.storyai.backend.job.dto;

import com.storyai.backend.domain.storycharacter.CharacterRole;
import com.storyai.backend.domain.videojob.AgeGroup;
import com.storyai.backend.domain.videojob.BookStyle;
import com.storyai.backend.domain.videojob.OutputType;
import com.storyai.backend.domain.videojob.StoryTheme;
import com.storyai.backend.domain.videojob.VideoStyle;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 생성 요청. outputType에 따라 책/영상 옵션 중 해당하는 값이 필요하며,
 * 조건부 필수 검증(예: BOOK이면 bookStyle/bookPages)은 VideoJobService에서 수행한다.
 */
public record CreateVideoJobRequest(
        @NotNull OutputType outputType,
        // theme 또는 customTheme 중 하나는 필수(조건부 검증은 VideoJobService에서).
        StoryTheme theme,
        /** 직접입력 주제(선택). 있으면 이야기·의상이 이 문구를 따른다. */
        String customTheme,
        @NotNull AgeGroup ageGroup,
        String mood,

        // 헌정 메세지(선택) · 스토리 방향(선택, 있으면 스토리에 반영)
        String dedication,
        // 헌정 페이지용 가족 사진 URL(선택) — 변환 없이 원본 그대로 삽입
        String dedicationPhotoUrl,
        String storyDirection,

        // 책 옵션
        BookStyle bookStyle,
        Integer bookPages,
        boolean physicalBookRequested,

        // 영상 옵션
        VideoStyle videoStyle,
        Integer videoDurationSec,

        // 등장인물 (1명 이상, 첫 번째가 주인공)
        @NotEmpty @Valid List<CharacterInput> characters
) {
    public record CharacterInput(
            @NotBlank String name,
            @NotNull CharacterRole role,
            /** role=CUSTOM일 때 직접 입력한 관계(예: "이모", "할머니"). */
            String customRole,
            @NotEmpty List<@NotBlank String> photoUrls
    ) {
    }
}
