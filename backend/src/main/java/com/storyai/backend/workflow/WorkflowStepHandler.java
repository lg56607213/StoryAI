package com.storyai.backend.workflow;

import com.storyai.backend.domain.videojob.VideoJob;
import com.storyai.backend.domain.videojob.WorkflowStep;

/**
 * 파이프라인의 한 단계를 처리하는 핸들러. WorkflowStep 당 정확히 하나의 스프링 빈이 존재해야 한다.
 * 지금은 각 구현체가 AI 호출 없이 자리표시자(placeholder) 데이터를 채우지만,
 * 실제 AI 연동 시 execute() 내부만 교체하면 되므로 엔진/도메인 모델에는 영향이 없다.
 */
public interface WorkflowStepHandler {

    WorkflowStep getStep();

    void execute(VideoJob job);
}
