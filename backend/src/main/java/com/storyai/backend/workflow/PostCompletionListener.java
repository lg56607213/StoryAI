package com.storyai.backend.workflow;

import com.storyai.backend.video.NarrationVideoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 전체 생성 완료 후처리. 커밋된 데이터(완성된 페이지)를 보고 낭독 영상을 자동 생성한다.
 * AFTER_COMMIT으로 받아 워크플로우 트랜잭션과 분리하고, generateAsync가 미디어 전용 풀에서 돈다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostCompletionListener {

    private final NarrationVideoService narrationVideoService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onJobFullyCompleted(JobFullyCompletedEvent event) {
        log.info("전체 완성 후처리: 낭독 영상 자동 생성 시작 job={}", event.jobId());
        narrationVideoService.generateAsync(event.jobId());
    }
}
