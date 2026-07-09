package com.storyai.backend.workflow;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 워크플로우 진입점. start(jobId)를 호출하면 커밋 이후 비동기로 첫 스텝이 실행되고,
 * 이후 각 스텝은 WorkflowStepExecutor가 다음 스텝의 AdvanceWorkflowEvent를 발행하는 방식으로
 * 스스로 이어진다 (재귀 호출이 아니라 스프링 이벤트 버스를 통한 체이닝).
 */
@Component
@RequiredArgsConstructor
public class WorkflowEngine {

    private final ApplicationEventPublisher eventPublisher;
    private final WorkflowStepExecutor stepExecutor;

    public void start(Long jobId) {
        eventPublisher.publishEvent(new AdvanceWorkflowEvent(jobId));
    }

    @Async("workflowTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAdvanceWorkflowEvent(AdvanceWorkflowEvent event) {
        stepExecutor.advance(event.jobId());
    }
}
