package com.storyai.backend.domain.videojob;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface VideoJobRepository extends JpaRepository<VideoJob, Long> {

    /** 관리자 통계: 특정 시점 이후 생성된 주문(오름차순). */
    List<VideoJob> findByCreatedAtAfterOrderByCreatedAtAsc(LocalDateTime since);

    /** 관리자 목록: 구매요청(확정)된 주문을 확정시각 내림차순으로. */
    List<VideoJob> findTop200ByConfirmedAtIsNotNullOrderByConfirmedAtDesc();

    /** 관리자 상세: 최근 생성 내역(미리보기 포함) 전체를 최신순으로. */
    List<VideoJob> findTop300ByOrderByCreatedAtDesc();

    /**
     * 하루 생성 제한: 이 계정이 오늘 만든 건수.
     * 실패한 건은 사용자 탓이 아니므로 한도에서 제외한다(서버 오류로 할당량이 깎이면 안 됨).
     */
    long countByRequesterEmailAndCreatedAtAfterAndStatusNot(
            String requesterEmail, LocalDateTime since, JobStatus excludedStatus);

    /** 마이페이지: 이 계정의 동화책 목록(최신순). */
    List<VideoJob> findByRequesterEmailOrderByCreatedAtDesc(String requesterEmail);

    /** 재시작 복구: 진행 중이던(또는 시작도 못 한) 작업 찾기. */
    List<VideoJob> findByStatusIn(List<JobStatus> statuses);

    /**
     * 영상 재시도 대상: 완성(COMPLETED)됐고 영상 포함 주문인데 아직 영상이 없는(URL 미생성) 건.
     * (narrationVideoStatus가 failed 또는 멈춘 generating인 경우를 스위퍼에서 걸러 재시도한다.)
     */
    List<VideoJob> findByStatusAndVideoIncludedTrueAndNarrationVideoUrlIsNull(JobStatus status);
}
