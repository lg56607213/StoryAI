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
        @NotNull StoryTheme theme,
        @NotNull AgeGroup ageGroup,
        String mood,

        // 헌정 메세지(선택) · 스토리 방향(선택, 있으면 스토리에 반영)
        String dedication,
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
            @NotEmpty List<@NotBlank String> photoUrls
    ) {
    }
}
