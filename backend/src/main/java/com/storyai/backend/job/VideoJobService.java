package com.storyai.backend.job;

import com.storyai.backend.domain.media.MediaAsset;
import com.storyai.backend.domain.media.MediaAssetRepository;
import com.storyai.backend.domain.media.MediaType;
import com.storyai.backend.domain.videojob.VideoJob;
import com.storyai.backend.domain.videojob.VideoJobRepository;
import com.storyai.backend.domain.videojob.WorkflowStep;
import com.storyai.backend.job.dto.CreateVideoJobRequest;
import com.storyai.backend.workflow.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VideoJobService {

    private final VideoJobRepository videoJobRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final WorkflowEngine workflowEngine;

    @Transactional
    public VideoJob createJob(CreateVideoJobRequest request) {
        VideoJob job = VideoJob.builder()
                .theme(request.theme())
                .protagonistDescription(request.protagonistDescription())
                .mood(request.mood())
                .targetLengthSeconds(request.targetLengthSeconds())
                .targetAgeGroup(request.targetAgeGroup())
                .currentStep(WorkflowStep.first())
                .build();
        job = videoJobRepository.save(job);

        for (String photoUrl : request.photoUrls()) {
            MediaAsset photo = MediaAsset.builder()
                    .videoJob(job)
                    .type(MediaType.SOURCE_PHOTO)
                    .url(photoUrl)
                    .build();
            mediaAssetRepository.save(photo);
        }

        // 커밋 이후 비동기로 워크플로우 첫 단계가 시작된다.
        workflowEngine.start(job.getId());
        return job;
    }

    @Transactional(readOnly = true)
    public VideoJob getJob(Long jobId) {
        return videoJobRepository.findById(jobId)
                .orElseThrow(() -> new VideoJobNotFoundException(jobId));
    }
}
