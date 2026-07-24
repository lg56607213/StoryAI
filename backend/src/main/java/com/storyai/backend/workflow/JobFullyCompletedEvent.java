package com.storyai.backend.workflow;

/**
 * 전체(FULL) 생성이 끝나 완성된 주문에 대해 발행된다.
 * 커밋 이후(AFTER_COMMIT) 후처리(영상 포함 주문의 낭독 영상 자동 생성 등)를 트리거하는 데 쓴다.
 */
public record JobFullyCompletedEvent(Long jobId) {
}
