package com.storyai.backend.workflow.handler;

import com.storyai.backend.ai.story.StoryGenerator;
import com.storyai.backend.ai.story.StoryOutline;
import com.storyai.backend.domain.videojob.VideoJob;
import com.storyai.backend.domain.videojob.WorkflowStep;
import com.storyai.backend.workflow.WorkflowStepHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StoryGenerationStepHandler implements WorkflowStepHandler {

    private final StoryGenerator storyGenerator;

    @Override
    public WorkflowStep getStep() {
        return WorkflowStep.STORY_GENERATION;
    }

    @Override
    public void execute(VideoJob job) {
        StoryOutline outline = generateOutline(job);
        job.setGeneratedTitle(outline.title());
        job.setSynopsis(outline.synopsis());
    }

    private StoryOutline generateOutline(VideoJob job) {
        if (storyGenerator.isAvailable()) {
            try {
                return storyGenerator.outline(
                        job.getStoryTheme(), job.getTheme(), job.getProtagonistDescription(),
                        job.getAgeGroup(), job.getStoryDirection());
            } catch (Exception e) {
                log.warn("스토리 생성 실패, 더미로 폴백: {}", e.getMessage());
            }
        }
        // 키 미설정/오류 시 폴백 (파이프라인·테스트가 멈추지 않도록)
        return new StoryOutline(
                "%s 이야기".formatted(job.getTheme()),
                "%s(이)가 %s을(를) 겪는 이야기".formatted(job.getProtagonistDescription(), job.getTheme()));
    }
}
