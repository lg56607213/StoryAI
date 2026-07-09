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
public class SubtitleGenerationStepHandler implements WorkflowStepHandler {

    private final SceneRepository sceneRepository;

    @Override
    public WorkflowStep getStep() {
        return WorkflowStep.SUBTITLE_GENERATION;
    }

    @Override
    public void execute(VideoJob job) {
        List<Scene> scenes = sceneRepository.findByVideoJobIdOrderBySceneNumberAsc(job.getId());

        // TODO: Subtitle Engine 연동 지점.
        // voiceAudioUrl(또는 narration 텍스트)로부터 타임스탬프가 포함된 자막을 생성하도록 교체한다.
        for (Scene scene : scenes) {
            scene.setSubtitleText(scene.getNarration());
        }
        sceneRepository.saveAll(scenes);
    }
}
