package com.storyai.backend.workflow.handler;

import com.storyai.backend.domain.videojob.VideoJob;
import com.storyai.backend.domain.videojob.WorkflowStep;
import com.storyai.backend.workflow.WorkflowStepHandler;
import org.springframework.stereotype.Component;

@Component
public class StoryGenerationStepHandler implements WorkflowStepHandler {

    @Override
    public WorkflowStep getStep() {
        return WorkflowStep.STORY_GENERATION;
    }

    @Override
    public void execute(VideoJob job) {
        // TODO: Story AI(Claude API 등) 연동 지점.
        // job.getTheme() / getProtagonistDescription() / getMood() / getTargetAgeGroup()을
        // 프롬프트로 넘겨 제목/줄거리를 구조화된 형태로 받아온다.
        job.setGeneratedTitle("%s 이야기".formatted(job.getTheme()));
        job.setSynopsis("%s(이)가 %s을(를) 겪는 이야기".formatted(job.getProtagonistDescription(), job.getTheme()));
    }
}
