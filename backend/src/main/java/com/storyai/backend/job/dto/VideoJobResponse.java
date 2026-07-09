package com.storyai.backend.job.dto;

import com.storyai.backend.domain.videojob.JobStatus;
import com.storyai.backend.domain.videojob.VideoJob;
import com.storyai.backend.domain.videojob.WorkflowStep;

import java.time.LocalDateTime;

public record VideoJobResponse(
        Long id,
        JobStatus status,
        WorkflowStep currentStep,
        String generatedTitle,
        String resultVideoUrl,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static VideoJobResponse from(VideoJob job) {
        return new VideoJobResponse(
                job.getId(),
                job.getStatus(),
                job.getCurrentStep(),
                job.getGeneratedTitle(),
                job.getResultVideoUrl(),
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }
}
