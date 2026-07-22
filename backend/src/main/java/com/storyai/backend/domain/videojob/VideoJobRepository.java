package com.storyai.backend.domain.videojob;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface VideoJobRepository extends JpaRepository<VideoJob, Long> {

    /** 관리자 통계: 특정 시점 이후 생성된 주문(오름차순). */
    List<VideoJob> findByCreatedAtAfterOrderByCreatedAtAsc(LocalDateTime since);

    /** 관리자 목록: 구매요청(확정)된 주문을 확정시각 내림차순으로. */
    List<VideoJob> findTop200ByConfirmedAtIsNotNullOrderByConfirmedAtDesc();
}
