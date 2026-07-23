package com.storyai.backend.workflow;

import com.storyai.backend.domain.videojob.JobStatus;
import com.storyai.backend.domain.videojob.VideoJob;
import com.storyai.backend.domain.videojob.VideoJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 서버 재시작으로 끊긴 작업을 살려낸다.
 *
 * 워크플로우는 스프링 이벤트로만 이어지므로(메모리 기반), 배포·재시작이 일어나면
 * 진행 중이던 작업은 DB에 RUNNING으로 남은 채 아무도 이어받지 않아 영원히 "제작 중"이 된다.
 * 부팅 완료 시점에 그런 작업을 찾아 현재 단계부터 재개하고, 너무 오래된 건은 실패로 정리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowRecovery {

    private final VideoJobRepository videoJobRepository;
    private final WorkflowEngine workflowEngine;

    /** 이 시간 안에 갱신된 작업만 재개한다(오래된 건은 비용·혼선 방지를 위해 실패 처리). */
    @Value("${storyai.workflow.resume-window-hours:6}")
    private int resumeWindowHours;

    /** 한 번에 재개할 최대 건수(재시작 직후 과부하 방지). */
    @Value("${storyai.workflow.resume-max:20}")
    private int resumeMax;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void resumeInterruptedJobs() {
        List<VideoJob> stuck = videoJobRepository.findByStatusIn(
                List.of(JobStatus.RUNNING, JobStatus.PENDING));
        if (stuck.isEmpty()) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusHours(Math.max(1, resumeWindowHours));

        int resumed = 0, failed = 0;
        for (VideoJob job : stuck) {
            LocalDateTime touched = job.getUpdatedAt() != null ? job.getUpdatedAt() : job.getCreatedAt();
            boolean recent = touched != null && touched.isAfter(cutoff);

            if (recent && resumed < resumeMax) {
                if (job.getCurrentStep() == null) {
                    job.moveToStep(com.storyai.backend.domain.videojob.WorkflowStep.first());
                    videoJobRepository.save(job);
                }
                log.info("↻ 재시작 복구: job {} 를 {} 단계부터 재개", job.getId(), job.getCurrentStep());
                workflowEngine.start(job.getId());
                resumed++;
            } else {
                job.markFailed("서버 재시작으로 생성이 중단되었어요. 다시 만들어 주세요.");
                videoJobRepository.save(job);
                failed++;
            }
        }
        log.info("재시작 복구 완료: 재개 {}건, 중단처리 {}건 (총 {}건)", resumed, failed, stuck.size());
    }
}
