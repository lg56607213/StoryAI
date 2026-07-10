package com.storyai.backend.workflow;

import com.storyai.backend.domain.videojob.JobStatus;
import com.storyai.backend.domain.videojob.VideoJob;
import com.storyai.backend.domain.videojob.VideoJobRepository;
import com.storyai.backend.domain.videojob.WorkflowStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * VideoJob 하나를 "현재 단계"만큼 한 스텝 전진시킨다.
 * 다음 스텝이 남아있으면 AdvanceWorkflowEvent를 다시 발행해서 WorkflowEngine이 비동기로
 * 이어받게 하고, 이 메서드 자신은 매 호출마다 독립적인 트랜잭션으로 종료된다.
 *
 * WorkflowEngine과 클래스를 분리한 이유: 같은 빈 안에서 이 메서드를 직접 호출하면
 * @Transactional 프록시가 적용되지 않는 self-invocation 문제가 발생하기 때문이다.
 */
@Slf4j
@Component
public class WorkflowStepExecutor {

    private final VideoJobRepository videoJobRepository;
    private final Map<WorkflowStep, WorkflowStepHandler> handlersByStep;
    private final ApplicationEventPublisher eventPublisher;
    private final WorkflowPlan workflowPlan;

    public WorkflowStepExecutor(VideoJobRepository videoJobRepository,
                                 List<WorkflowStepHandler> stepHandlers,
                                 ApplicationEventPublisher eventPublisher,
                                 WorkflowPlan workflowPlan) {
        this.videoJobRepository = videoJobRepository;
        this.handlersByStep = stepHandlers.stream()
                .collect(Collectors.toMap(WorkflowStepHandler::getStep, Function.identity()));
        this.eventPublisher = eventPublisher;
        this.workflowPlan = workflowPlan;
    }

    @Transactional
    public void advance(Long jobId) {
        VideoJob job = videoJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("VideoJob not found: " + jobId));

        if (job.getStatus() == JobStatus.COMPLETED || job.getStatus() == JobStatus.FAILED) {
            return;
        }

        WorkflowStep step = job.getCurrentStep();
        WorkflowStepHandler handler = handlersByStep.get(step);
        if (handler == null) {
            throw new IllegalStateException("No handler registered for workflow step: " + step);
        }

        job.markRunning();

        try {
            handler.execute(job);
        } catch (Exception e) {
            log.error("Workflow step {} failed for job {}", step, jobId, e);
            job.markFailed(e.getMessage());
            videoJobRepository.save(job);
            return;
        }

        Optional<WorkflowStep> nextStep = workflowPlan.next(job.getOutputType(), step);
        if (nextStep.isPresent()) {
            job.moveToStep(nextStep.get());
            videoJobRepository.save(job);
            eventPublisher.publishEvent(new AdvanceWorkflowEvent(jobId));
        } else {
            job.markCompleted();
            videoJobRepository.save(job);
        }
    }
}
