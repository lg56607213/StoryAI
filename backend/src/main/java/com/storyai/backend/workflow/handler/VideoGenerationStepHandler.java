package com.storyai.backend.workflow.handler;

import com.storyai.backend.domain.scene.Scene;
import com.storyai.backend.domain.scene.SceneRepository;
import com.storyai.backend.domain.videojob.VideoJob;
import com.storyai.backend.domain.videojob.WorkflowStep;
import com.storyai.backend.workflow.WorkflowStepHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class VideoGenerationStepHandler implements WorkflowStepHandler {

    private final SceneRepository sceneRepository;

    @Override
    public WorkflowStep getStep() {
        return WorkflowStep.VIDEO_GENERATION;
    }

    @Override
    public void execute(VideoJob job) {
        List<Scene> scenes = sceneRepository.findByVideoJobIdOrderBySceneNumberAsc(job.getId());

        // TODO: Video Generator AI(Kling/Runway/Luma 등) 연동 지점.
        // scene.getImageUrl()을 입력으로 6~10초 분량의 장면 영상을 생성하도록 교체한다.
        for (Scene scene : scenes) {
            scene.setVideoUrl("https://placeholder.storyai/%d/scene-%d-video.mp4".formatted(job.getId(), scene.getSceneNumber()));
        }
        sceneRepository.saveAll(scenes);
    }
}
