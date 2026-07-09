package com.storyai.backend.workflow.handler;

import com.storyai.backend.domain.media.MediaAsset;
import com.storyai.backend.domain.media.MediaAssetRepository;
import com.storyai.backend.domain.media.MediaType;
import com.storyai.backend.domain.videojob.VideoJob;
import com.storyai.backend.domain.videojob.WorkflowStep;
import com.storyai.backend.workflow.WorkflowStepHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VideoCompositionStepHandler implements WorkflowStepHandler {

    private final MediaAssetRepository mediaAssetRepository;

    @Override
    public WorkflowStep getStep() {
        return WorkflowStep.VIDEO_COMPOSITION;
    }

    @Override
    public void execute(VideoJob job) {
        // TODO: FFmpeg 합성 워커 연동 지점.
        // 장면별 video/voice/subtitle을 하나의 MP4로 합쳐 스토리지(S3)에 업로드하도록 교체한다.
        String resultUrl = "https://placeholder.storyai/%d/final.mp4".formatted(job.getId());
        job.setResultVideoUrl(resultUrl);

        MediaAsset finalVideo = MediaAsset.builder()
                .videoJob(job)
                .type(MediaType.FINAL_VIDEO)
                .url(resultUrl)
                .build();
        mediaAssetRepository.save(finalVideo);
    }
}
