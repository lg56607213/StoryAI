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
public class VoiceGenerationStepHandler implements WorkflowStepHandler {

    private final SceneRepository sceneRepository;

    @Override
    public WorkflowStep getStep() {
        return WorkflowStep.VOICE_GENERATION;
    }

    @Override
    public void execute(VideoJob job) {
        List<Scene> scenes = sceneRepository.findByVideoJobIdOrderBySceneNumberAsc(job.getId());

        // TODO: Voice Engine(ElevenLabs 등) 연동 지점.
        // scene.getNarration() 텍스트를 음성으로 합성하도록 교체한다.
        for (Scene scene : scenes) {
            scene.setVoiceAudioUrl("https://placeholder.storyai/%d/scene-%d-voice.mp3".formatted(job.getId(), scene.getSceneNumber()));
        }
        sceneRepository.saveAll(scenes);
    }
}
