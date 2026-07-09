package com.storyai.backend.job.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CreateVideoJobRequest(
        @NotBlank String theme,
        @NotBlank String protagonistDescription,
        String mood,
        @Positive Integer targetLengthSeconds,
        String targetAgeGroup,
        @NotEmpty List<@NotBlank String> photoUrls
) {
}
