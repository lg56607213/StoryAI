package com.storyai.backend.admin;

import com.storyai.backend.auth.AdminGuard;
import com.storyai.backend.domain.videojob.JobStatus;
import com.storyai.backend.domain.videojob.OutputType;
import com.storyai.backend.domain.videojob.VideoJob;
import com.storyai.backend.domain.videojob.VideoJobRepository;
import com.storyai.backend.pricing.Pricing;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 관리자 대시보드 API. 관리자(ADMIN_EMAILS)만 접근 가능.
 * - GET /api/admin/stats?days=30 : 일자별 미리보기/구매요청 수 + 예상 원가
 * - GET /api/admin/purchases     : 구매요청(확정) 목록(누가·무엇·얼마)
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    /** 실측 기반 이미지 1장당 예상 원가(원). 청구서 기준 ~118원. */
    private static final int COST_PER_IMAGE_KRW = 118;
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    private final VideoJobRepository videoJobRepository;
    private final AdminGuard adminGuard;

    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestParam(defaultValue = "30") int days, Authentication auth) {
        adminGuard.require(auth);
        int range = Math.max(1, Math.min(days, 180));
        LocalDate today = LocalDate.now();
        LocalDateTime since = today.minusDays(range - 1L).atStartOfDay();
        List<VideoJob> jobs = videoJobRepository.findByCreatedAtAfterOrderByCreatedAtAsc(since);

        // 날짜별 카운트: [미리보기 생성, 구매요청, PDF, 하드커버, 완성]
        Map<LocalDate, long[]> byDate = new TreeMap<>();
        for (int i = 0; i < range; i++) {
            byDate.put(today.minusDays(range - 1L - i), new long[5]);
        }
        long totalImages = 0;
        for (VideoJob j : jobs) {
            LocalDate created = j.getCreatedAt().toLocalDate();
            bucket(byDate, created)[0]++; // 미리보기(생성) 1건
            if (j.getStatus() == JobStatus.COMPLETED) {
                bucket(byDate, created)[4]++;
            }
            if (j.getConfirmedAt() != null) {
                LocalDate conf = j.getConfirmedAt().toLocalDate();
                long[] b = bucket(byDate, conf);
                b[1]++; // 구매요청
                if (j.isPhysicalBookRequested()) {
                    b[3]++;
                } else {
                    b[2]++;
                }
            }
            totalImages += estimateImages(j);
        }

        List<Map<String, Object>> daily = new ArrayList<>();
        long tPrev = 0, tBuy = 0, tPdf = 0, tBook = 0, tDone = 0;
        for (Map.Entry<LocalDate, long[]> e : byDate.entrySet()) {
            long[] v = e.getValue();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", e.getKey().format(D));
            row.put("previews", v[0]);
            row.put("purchases", v[1]);
            row.put("pdf", v[2]);
            row.put("hardcover", v[3]);
            row.put("completed", v[4]);
            daily.add(row);
            tPrev += v[0]; tBuy += v[1]; tPdf += v[2]; tBook += v[3]; tDone += v[4];
        }

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("previews", tPrev);
        totals.put("purchases", tBuy);
        totals.put("pdf", tPdf);
        totals.put("hardcover", tBook);
        totals.put("completed", tDone);

        Map<String, Object> res = new HashMap<>();
        res.put("days", range);
        res.put("daily", daily);
        res.put("totals", totals);
        res.put("estImages", totalImages);
        res.put("estCostKrw", totalImages * COST_PER_IMAGE_KRW);
        res.put("costPerImageKrw", COST_PER_IMAGE_KRW);
        return res;
    }

    @GetMapping("/purchases")
    public List<Map<String, Object>> purchases(Authentication auth) {
        adminGuard.require(auth);
        List<Map<String, Object>> out = new ArrayList<>();
        for (VideoJob j : videoJobRepository.findTop200ByConfirmedAtIsNotNullOrderByConfirmedAtDesc()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", j.getId());
            m.put("confirmedAt", j.getConfirmedAt() != null ? j.getConfirmedAt().format(DT) : null);
            m.put("type", j.isPhysicalBookRequested() ? "하드커버" : "PDF");
            m.put("pages", j.getBookPages());
            m.put("priceKrw", Pricing.priceKrw(j));
            m.put("title", j.getGeneratedTitle());
            m.put("deliveryEmail", j.getDeliveryEmail());
            m.put("requesterEmail", j.getRequesterEmail());
            m.put("requesterProvider", j.getRequesterProvider());
            m.put("status", j.getStatus() != null ? j.getStatus().name() : null);
            out.add(m);
        }
        return out;
    }

    /** 주문 1건의 예상 이미지 생성 수(원가 추정). 미리보기=6, 확정(전체)=2시트+페이지. */
    private int estimateImages(VideoJob j) {
        if (j.getOutputType() != OutputType.BOOK) {
            return 0;
        }
        if (j.getConfirmedAt() != null) {
            int pages = j.getBookPages() != null ? j.getBookPages() : 24;
            return 2 + pages;
        }
        return 6;
    }

    private long[] bucket(Map<LocalDate, long[]> byDate, LocalDate d) {
        return byDate.computeIfAbsent(d, k -> new long[5]);
    }
}
