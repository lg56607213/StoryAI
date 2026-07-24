package com.storyai.backend.workflow;

import com.storyai.backend.domain.videojob.BookPhase;
import com.storyai.backend.domain.videojob.JobStatus;
import com.storyai.backend.domain.videojob.VideoJob;
import com.storyai.backend.domain.videojob.VideoJobRepository;
import com.storyai.backend.domain.videojob.WorkflowStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 확정(구매)된 주문이 서버 중단·오류로 멈추거나 실패했을 때 주기적으로 자동 재개한다.
 *
 * 부팅 시점의 WorkflowRecovery만으로는 (a)재시작 없이 스텝이 죽은 경우, (b)한 번 실패로 정리된 경우를
 * 되살리지 못한다. 이 스위퍼가 몇 분마다 돌며 "고객이 기다리는(확정된) 전체생성 주문" 중
 * 멈췄거나 실패한 건을 상한 횟수까지 이어서 재시도한다 → 시간이 걸려도 결국 완성·발송되게 한다.
 *
 * 이미 만든 삽화는 재사용(비용 절약)되고, 동시 생성은 전역 게이트로 제한되므로 재시도가 서버를
 * 다시 무너뜨리지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StalledJobSweeper {

    private final VideoJobRepository videoJobRepository;
    private final WorkflowEngine workflowEngine;
    private final com.storyai.backend.video.NarrationVideoService narrationVideoService;

    /** 이 시간(시간) 안에 갱신된 확정 주문만 재개 대상(너무 오래된 건 최종 실패). */
    @Value("${storyai.workflow.resume-window-hours:6}")
    private int windowHours;

    /** RUNNING인데 이 시간(분) 동안 진행이 없으면 "멈춤"으로 보고 재개(정상 진행 중 오탐 방지 위해 넉넉히). */
    @Value("${storyai.workflow.stall-minutes:15}")
    private int stallMinutes;

    /** 자동 재시도 최대 횟수(넘으면 최종 실패로 두어 무한 반복 방지). */
    @Value("${storyai.workflow.max-recovery-attempts:5}")
    private int maxAttempts;

    /** 한 번의 스윕에서 재개할 최대 건수(부하 급증 방지). */
    @Value("${storyai.workflow.sweep-batch:3}")
    private int sweepBatch;

    /** 낭독 영상이 이 시간(분) 이상 generating이면 멈춘 것으로 보고 재생성(영상은 오래 걸리므로 넉넉히). */
    @Value("${storyai.workflow.video-stall-minutes:25}")
    private int videoStallMinutes;

    /** 낭독 영상 자동 재시도 최대 횟수. */
    @Value("${storyai.workflow.video-max-recovery-attempts:5}")
    private int videoMaxAttempts;

    @Scheduled(fixedDelayString = "${storyai.workflow.sweep-ms:180000}",
            initialDelayString = "${storyai.workflow.sweep-initial-ms:90000}")
    @Transactional
    public void sweep() {
        List<VideoJob> candidates = videoJobRepository.findByStatusIn(
                List.of(JobStatus.RUNNING, JobStatus.FAILED));
        if (candidates.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowCutoff = now.minusHours(Math.max(1, windowHours));
        LocalDateTime stallCutoff = now.minusMinutes(Math.max(3, stallMinutes));

        int retried = 0;
        for (VideoJob job : candidates) {
            if (retried >= sweepBatch) {
                break;
            }
            // 고객이 기다리는(확정된) 전체생성 주문만 자동 재개한다.
            // 미리보기·미확정 실험은 자동 재시도하지 않는다(고객이 재주문하면 됨).
            if (job.getConfirmedAt() == null || job.getBookPhase() != BookPhase.FULL) {
                continue;
            }
            LocalDateTime touched = job.getUpdatedAt() != null ? job.getUpdatedAt() : job.getCreatedAt();

            // 너무 오래된 건: 최종 실패로 정리(자동 재개 대상에서 제외).
            if (touched == null || touched.isBefore(windowCutoff)) {
                if (job.getStatus() == JobStatus.RUNNING) {
                    job.markFailed("서버 문제로 오래 진행되지 않아 중단되었어요. 다시 시도해 주세요.");
                    videoJobRepository.save(job);
                }
                continue;
            }
            // RUNNING이면서 최근 갱신됐으면 정상 진행 중 → 건너뜀.
            if (job.getStatus() == JobStatus.RUNNING && touched.isAfter(stallCutoff)) {
                continue;
            }
            // 재시도 횟수 초과: 더는 자동 재개하지 않고 최종 실패로 남긴다.
            if (job.getRecoveryAttempts() >= maxAttempts) {
                if (job.getStatus() == JobStatus.RUNNING) {
                    job.markFailed("여러 번 자동으로 다시 시도했지만 완성하지 못했어요. 고객센터로 문의해 주세요.");
                    videoJobRepository.save(job);
                }
                continue;
            }

            if (job.getCurrentStep() == null) {
                job.moveToStep(WorkflowStep.PAGE_ILLUSTRATION);
            }
            job.markForRetry();
            videoJobRepository.save(job);
            log.info("↻ 자동 재개(스윕): job {} 를 {} 단계부터 재시도 (attempt {})",
                    job.getId(), job.getCurrentStep(), job.getRecoveryAttempts());
            workflowEngine.start(job.getId());
            retried++;
        }
        if (retried > 0) {
            log.info("자동 재개 스윕: {}건 재시도", retried);
        }

        sweepVideos(now, windowCutoff);
    }

    /**
     * 낭독 영상 재시도: PDF는 완성돼 발송됐지만 영상이 아직 안 만들어진(실패·멈춤) 확정 주문을
     * 상한까지 다시 생성해 결국 고객 메일로 보낸다(PDF와 동일한 "결국 전송" 보장).
     */
    private void sweepVideos(LocalDateTime now, LocalDateTime windowCutoff) {
        List<VideoJob> targets = videoJobRepository
                .findByStatusAndVideoIncludedTrueAndNarrationVideoUrlIsNull(JobStatus.COMPLETED);
        if (targets.isEmpty()) {
            return;
        }
        LocalDateTime videoStallCutoff = now.minusMinutes(Math.max(5, videoStallMinutes));
        int retried = 0;
        for (VideoJob job : targets) {
            if (retried >= sweepBatch) {
                break;
            }
            if (job.getConfirmedAt() == null || job.getBookPhase() != BookPhase.FULL) {
                continue; // 확정(구매)됐고 전체생성(FULL)까지 끝난 주문만 — 미리보기 단계는 영상 생성 금지
            }
            LocalDateTime touched = job.getUpdatedAt() != null ? job.getUpdatedAt() : job.getCreatedAt();
            if (touched == null || touched.isBefore(windowCutoff)) {
                continue; // 너무 오래된 건은 자동 재시도 제외(관리자가 수동 처리)
            }
            if (job.getVideoRecoveryAttempts() >= videoMaxAttempts) {
                continue; // 상한 초과 → 그만 시도
            }
            String vs = job.getNarrationVideoStatus();
            boolean failed = "failed".equalsIgnoreCase(vs);
            // generating/미시작이지만 오래 진행이 없으면 멈춘 것으로 보고 재시도(방금 시작된 건은 제외).
            boolean stalled = (vs == null || vs.isBlank() || "generating".equalsIgnoreCase(vs))
                    && touched.isBefore(videoStallCutoff);
            if (!failed && !stalled) {
                continue; // 정상 진행 중이거나 방금 시작 → 건너뜀
            }
            job.markVideoForRetry();
            videoJobRepository.save(job);
            log.info("↻ 낭독 영상 자동 재시도(스윕): job {} (attempt {})",
                    job.getId(), job.getVideoRecoveryAttempts());
            narrationVideoService.generateAsync(job.getId());
            retried++;
        }
        if (retried > 0) {
            log.info("영상 자동 재시도 스윕: {}건", retried);
        }
    }
}
