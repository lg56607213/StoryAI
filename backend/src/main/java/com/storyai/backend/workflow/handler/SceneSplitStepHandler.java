package com.storyai.backend.workflow.handler;

import com.storyai.backend.domain.scene.Scene;
import com.storyai.backend.domain.scene.SceneRepository;
import com.storyai.backend.domain.videojob.VideoJob;
import com.storyai.backend.domain.videojob.WorkflowStep;
import com.storyai.backend.workflow.WorkflowStepHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SceneSplitStepHandler implements WorkflowStepHandler {

    private static final int DEFAULT_SCENE_COUNT = 6;

    private final SceneRepository sceneRepository;

    @Override
    public WorkflowStep getStep() {
        return WorkflowStep.SCENE_SPLIT;
    }

    @Override
    public void execute(VideoJob job) {
        // TODO: Scene Generator AI 연동 지점.
        // job.getSynopsis()를 6~15개 장면(대본/내레이션/장면설명)으로 분리하도록 교체한다.
        for (int i = 1; i <= DEFAULT_SCENE_COUNT; i++) {
            Scene scene = Scene.builder()
                    .videoJob(job)
                    .sceneNumber(i)
                    .script("%s - Scene %d".formatted(job.getSynopsis(), i))
                    .narration("Scene %d narration placeholder".formatted(i))
                    .sceneDescription("Scene %d visual description placeholder".formatted(i))
                    .build();
            sceneRepository.save(scene);
        }
    }
}
