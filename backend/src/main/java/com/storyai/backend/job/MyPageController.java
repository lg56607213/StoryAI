package com.storyai.backend.job;

import com.storyai.backend.auth.LoginIdentity;
import com.storyai.backend.domain.bookpage.BookPage;
import com.storyai.backend.domain.bookpage.BookPageRepository;
import com.storyai.backend.domain.videojob.BookPhase;
import com.storyai.backend.domain.videojob.JobStatus;
import com.storyai.backend.domain.videojob.VideoJob;
import com.storyai.backend.domain.videojob.VideoJobRepository;
import com.storyai.backend.pricing.Pricing;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 고객 마이페이지 API — 본인이 만든 동화책 이력과 하루 생성 한도를 제공한다.
 * 모든 응답은 로그인한 본인 것만 반환한다(다른 사람 주문 접근 불가).
 */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MyPageController {

    private final VideoJobRepository videoJobRepository;
    private final BookPageRepository bookPageRepository;
    private final com.storyai.backend.auth.AdminGuard adminGuard;

    @Value("${storyai.rate-limit.previews-per-user-per-day:3}")
    private int previewsPerUserPerDay;

    /** 내 동화책 목록(최신순). 숨김 처리한 건은 제외. */
    @GetMapping("/books")
    public List<MyBook> books(Authentication auth) {
        String email = requireEmail(auth);
        return videoJobRepository.findByRequesterEmailOrderByCreatedAtDesc(email).stream()
                .filter(j -> !j.isHiddenByUser())
                .map(this::toMyBook)
                .toList();
    }

    /** 오늘 남은 생성 횟수. 실패한 건은 제외하며, 관리자는 무제한(-1). */
    @GetMapping("/quota")
    public Map<String, Object> quota(Authentication auth) {
        String email = requireEmail(auth);
        boolean unlimited = adminGuard.isAdmin(auth) || previewsPerUserPerDay <= 0;
        long used = unlimited ? 0
                : videoJobRepository.countByRequesterEmailAndCreatedAtAfterAndStatusNot(
                        email, LocalDate.now().atStartOfDay(), JobStatus.FAILED);
        int limit = Math.max(0, previewsPerUserPerDay);
        return Map.of(
                "limit", limit,
                "usedToday", used,
                "remaining", unlimited ? -1 : Math.max(0, limit - used)); // -1 = 무제한
    }

    /** 내 목록에서 숨기기(기록은 보존). */
    @DeleteMapping("/books/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void hide(@PathVariable Long id, Authentication auth) {
        String email = requireEmail(auth);
        VideoJob job = videoJobRepository.findById(id)
                .orElseThrow(() -> new VideoJobNotFoundException(id));
        if (!email.equalsIgnoreCase(job.getRequesterEmail())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인의 동화책만 삭제할 수 있어요.");
        }
        job.setHiddenByUser(true);
        videoJobRepository.save(job);
    }

    // ---------- 매핑 ----------

    private MyBook toMyBook(VideoJob j) {
        return new MyBook(
                j.getId(),
                j.getGeneratedTitle(),
                j.getTheme(),
                j.getBookPages(),
                stage(j),
                thumbnailOf(j),
                Pricing.priceKrw(j),
                j.getPurchaseType(),
                j.isVideoIncluded(),
                j.isPhysicalBookRequested(),
                j.getResultUrl(),
                j.getNarrationVideoUrl(),
                j.getNarrationVideoStatus(),
                j.isEmailSent(),
                j.getCreatedAt(),
                j.getConfirmedAt());
    }

    /** 고객 화면용 상태 라벨: 제작 중 / 미리보기 / 제작 완료 / 실패. */
    private String stage(VideoJob j) {
        if (j.getStatus() == JobStatus.FAILED) {
            return "실패";
        }
        if (j.getStatus() != JobStatus.COMPLETED) {
            return "제작 중";
        }
        return j.getBookPhase() == BookPhase.PREVIEW ? "미리보기" : "제작 완료";
    }

    /** 목록 썸네일 = 첫 삽화. 아직 없으면 null. */
    private String thumbnailOf(VideoJob j) {
        for (BookPage p : bookPageRepository.findByVideoJobIdOrderByPageNumberAsc(j.getId())) {
            if (p.getImageUrl() != null && !p.getImageUrl().isBlank()) {
                return p.getImageUrl();
            }
        }
        return null;
    }

    /** 카카오처럼 이메일이 없는 로그인도 식별되도록 식별 키를 쓴다. */
    private String requireEmail(Authentication auth) {
        String identity = LoginIdentity.identityOf(auth);
        if (identity == null || identity.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");
        }
        return identity;
    }

    /** 마이페이지 목록 항목. */
    public record MyBook(
            Long id,
            String title,
            String theme,
            Integer bookPages,
            String stage,
            String thumbnailUrl,
            Integer priceKrw,
            String purchaseType,
            boolean videoIncluded,
            boolean physicalBookRequested,
            String resultUrl,
            String narrationVideoUrl,
            String narrationVideoStatus,
            boolean emailSent,
            LocalDateTime createdAt,
            LocalDateTime confirmedAt
    ) {
    }
}
