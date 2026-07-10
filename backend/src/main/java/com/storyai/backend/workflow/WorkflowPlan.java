package com.storyai.backend.workflow;

import com.storyai.backend.domain.videojob.OutputType;
import com.storyai.backend.domain.videojob.WorkflowStep;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * outputType(책/영상)에 따른 파이프라인 단계 순서를 정의한다.
 * WorkflowStepExecutor는 이 플랜을 기준으로 "다음 단계"를 결정한다.
 */
@Component
public class WorkflowPlan {

    private static final List<WorkflowStep> VIDEO_STEPS = List.of(
            WorkflowStep.STORY_GENERATION,
            WorkflowStep.SCENE_SPLIT,
            WorkflowStep.CHARACTER_ANALYSIS,
            WorkflowStep.IMAGE_GENERATION,
            WorkflowStep.VIDEO_GENERATION,
            WorkflowStep.VOICE_GENERATION,
            WorkflowStep.SUBTITLE_GENERATION,
            WorkflowStep.VIDEO_COMPOSITION
    );

    private static final List<WorkflowStep> BOOK_STEPS = List.of(
            WorkflowStep.STORY_GENERATION,
            WorkflowStep.PAGE_PLANNING,
            WorkflowStep.CHARACTER_ANALYSIS,
            WorkflowStep.PAGE_ILLUSTRATION,
            WorkflowStep.PDF_GENERATION
    );

    public List<WorkflowStep> stepsFor(OutputType outputType) {
        return outputType == OutputType.BOOK ? BOOK_STEPS : VIDEO_STEPS;
    }

    public WorkflowStep first(OutputType outputType) {
        return stepsFor(outputType).get(0);
    }

    public Optional<WorkflowStep> next(OutputType outputType, WorkflowStep current) {
        List<WorkflowStep> steps = stepsFor(outputType);
        int idx = steps.indexOf(current);
        if (idx < 0 || idx + 1 >= steps.size()) {
            return Optional.empty();
        }
        return Optional.of(steps.get(idx + 1));
    }
}
