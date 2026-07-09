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
public class ImageGenerationStepHandler implements WorkflowStepHandler {

    private final SceneRepository sceneRepository;

    @Override
    public WorkflowStep getStep() {
        return WorkflowStep.IMAGE_GENERATION;
    }

    @Override
    public void execute(VideoJob job) {
        List<Scene> scenes = sceneRepository.findByVideoJobIdOrderBySceneNumberAsc(job.getId());

        // TODO: Image Generator AI 연동 지점.
        // scene.getSceneDescription() + job의 CharacterProfile 참조 이미지를 함께 넘겨
        // 장면마다 캐릭터 일관성이 유지된 이미지를 생성하도록 교체한다.
        for (Scene scene : scenes) {
            scene.setImageUrl("https://placeholder.storyai/%d/scene-%d-image.png".formatted(job.getId(), scene.getSceneNumber()));
        }
        sceneRepository.saveAll(scenes);
    }
}
