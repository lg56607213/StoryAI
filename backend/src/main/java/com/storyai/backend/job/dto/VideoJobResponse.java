package com.storyai.backend.job.dto;

import com.storyai.backend.domain.storycharacter.StoryCharacter;
import com.storyai.backend.domain.videojob.AgeGroup;
import com.storyai.backend.domain.videojob.BookPhase;
import com.storyai.backend.domain.videojob.BookStyle;
import com.storyai.backend.domain.videojob.JobStatus;
import com.storyai.backend.domain.videojob.OutputType;
import com.storyai.backend.domain.videojob.StoryTheme;
import com.storyai.backend.domain.videojob.VideoJob;
import com.storyai.backend.domain.videojob.VideoStyle;
import com.storyai.backend.domain.videojob.WorkflowStep;
import com.storyai.backend.pricing.Pricing;

import java.time.LocalDateTime;
import java.util.List;

public record VideoJobResponse(
        Long id,
        OutputType outputType,
        StoryTheme theme,
        AgeGroup ageGroup,
        String dedication,
        String storyDirection,
        BookStyle bookStyle,
        Integer bookPages,
        BookPhase bookPhase,
        boolean physicalBookRequested,
        boolean videoIncluded,
        VideoStyle videoStyle,
        Integer videoDurationSec,
        List<CharacterSummary> characters,
        JobStatus status,
        WorkflowStep currentStep,
        String generatedTitle,
        Integer priceKrw,
        String resultUrl,
        String resultVideoUrl,
        String narrationVideoUrl,
        String narrationVideoStatus,
        boolean hasParentVoice,
        String deliveryEmail,
        boolean emailSent,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record CharacterSummary(String name, String role) {
    }

    public static VideoJobResponse from(VideoJob job) {
        List<CharacterSummary> characters = job.getStoryCharacters().stream()
                .map(VideoJobResponse::toSummary)
                .toList();
        return new VideoJobResponse(
                job.getId(),
                job.getOutputType(),
                job.getStoryTheme(),
                job.getAgeGroup(),
                job.getDedication(),
                job.getStoryDirection(),
                job.getBookStyle(),
                job.getBookPages(),
                job.getBookPhase(),
                job.isPhysicalBookRequested(),
                job.isVideoIncluded(),
                job.getVideoStyle(),
                job.getVideoDurationSec(),
                characters,
                job.getStatus(),
                job.getCurrentStep(),
                job.getGeneratedTitle(),
                Pricing.priceKrw(job),
                job.getResultUrl(),
                job.getResultVideoUrl(),
                job.getNarrationVideoUrl(),
                job.getNarrationVideoStatus(),
                job.getParentVoiceId() != null && !job.getParentVoiceId().isBlank(),
                job.getDeliveryEmail(),
                job.isEmailSent(),
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }

    private static CharacterSummary toSummary(StoryCharacter c) {
        return new CharacterSummary(c.getName(), c.getRole().name());
    }
}
