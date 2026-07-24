package com.storyai.backend.storage;

import com.storyai.backend.domain.videojob.BookPhase;
import com.storyai.backend.domain.videojob.JobStatus;
import com.storyai.backend.domain.videojob.VideoJob;
import com.storyai.backend.domain.videojob.VideoJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 저장소(볼륨) 정리. 파일만 지우고 DB의 이력·주문정보는 보존한다.
 * 정리 원칙:
 *  - 실패한 건: 생성 이미지(gen/{id}) 삭제 — 쓸모없음
 *  - 미리보기만 하고 확정 안 한 건(오래된 것): gen/{id} 삭제 — PDF도 없으니 이력만 남김
 *  - 업로드 원본 사진(uploads/): 전량 삭제 대상(생성 끝나면 불필요 + 개인정보 파기)
 *  - 구매 완료 건의 PDF/영상: 절대 삭제하지 않음
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageCleanupService {

    private final StorageService storage;
    private final VideoJobRepository videoJobRepository;

    /** 이 일수보다 오래된 "미확정 미리보기"의 생성 이미지를 정리한다. */
    @Value("${storyai.storage.cleanup.preview-keep-days:3}")
    private int previewKeepDays;

    /** 현재 사용량 요약(관리자 화면). */
    public Map<String, Object> usage() {
        Map<String, Object> m = new LinkedHashMap<>();
        long free = storage.usableSpaceBytes();
        m.put("남은용량MB", free < 0 ? -1 : free / (1024 * 1024));
        m.put("업로드사진MB", storage.dirSizeBytes("uploads") / (1024 * 1024));
        m.put("생성이미지MB", storage.dirSizeBytes("gen") / (1024 * 1024));
        m.put("마스코트MB", storage.dirSizeBytes("shared") / (1024 * 1024));
        m.put("경로", storage.rootPath());
        return m;
    }

    /**
     * 안전 정리 실행. 삭제 결과 요약을 반환한다.
     * @param dropUploads 업로드 원본 사진을 전량 삭제할지(개인정보 파기 겸함)
     */
    @Transactional
    public Map<String, Object> cleanup(boolean dropUploads) {
        long freed = 0;
        int failedCleaned = 0, previewCleaned = 0;
        LocalDateTime cutoff = LocalDateTime.now().minusDays(Math.max(0, previewKeepDays));

        for (VideoJob job : videoJobRepository.findTop300ByOrderByCreatedAtDesc()) {
            boolean isFailed = job.getStatus() == JobStatus.FAILED;
            boolean isUnconfirmedPreview = job.getConfirmedAt() == null
                    && job.getBookPhase() == BookPhase.PREVIEW
                    && job.getCreatedAt() != null && job.getCreatedAt().isBefore(cutoff);

            // 확정(구매)된 건은 PDF/영상이 gen 밖에 있지만, 안전을 위해 gen 삭제 대상에서 제외한다.
            if (job.getConfirmedAt() != null) {
                continue;
            }
            if (isFailed) {
                freed += storage.deleteGenerated(job.getId());
                failedCleaned++;
            } else if (isUnconfirmedPreview) {
                freed += storage.deleteGenerated(job.getId());
                previewCleaned++;
            }
        }

        long uploadsFreed = 0;
        if (dropUploads) {
            uploadsFreed = storage.deleteAllUploads();
            freed += uploadsFreed;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("확보MB", freed / (1024 * 1024));
        result.put("실패건정리", failedCleaned);
        result.put("미확정미리보기정리", previewCleaned);
        result.put("업로드원본삭제MB", uploadsFreed / (1024 * 1024));
        result.put("남은용량MB", storage.usableSpaceBytes() / (1024 * 1024));
        log.info("저장소 정리 완료: {}", result);
        return result;
    }

    /** 매일 새벽 4시 자동 정리(업로드 원본 포함). 볼륨이 다시 차는 것을 예방. */
    @Scheduled(cron = "${storyai.storage.cleanup.cron:0 0 4 * * *}")
    public void scheduledCleanup() {
        try {
            cleanup(true);
        } catch (Exception e) {
            log.warn("예약 저장소 정리 실패: {}", e.getMessage());
        }
    }
}
