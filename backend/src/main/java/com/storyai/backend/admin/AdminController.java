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

    /** 전체 생성 내역(미리보기 포함) 상세 — 누가·언제·무엇을·어떤 단계. */
    @GetMapping("/jobs")
    public List<Map<String, Object>> jobs(Authentication auth) {
        adminGuard.require(auth);
        List<Map<String, Object>> out = new ArrayList<>();
        for (VideoJob j : videoJobRepository.findTop300ByOrderByCreatedAtDesc()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", j.getId());
            m.put("createdAt", j.getCreatedAt() != null ? j.getCreatedAt().format(DT) : null);
            m.put("requesterEmail", j.getRequesterEmail());
            m.put("requesterProvider", j.getRequesterProvider());
            m.put("stage", stage(j)); // 미리보기 / PDF구매 / 하드커버구매
            m.put("theme", j.getTheme());
            m.put("style", j.getBookStyle() != null ? j.getBookStyle().getLabel() : null);
            m.put("age", j.getAgeGroup() != null ? j.getAgeGroup().getLabel() : null);
            m.put("pages", j.getBookPages());
            m.put("characters", j.getProtagonistDescription());
            m.put("title", j.getGeneratedTitle());
            m.put("priceKrw", j.getConfirmedAt() != null ? Pricing.priceKrw(j) : null);
            m.put("deliveryEmail", j.getDeliveryEmail());
            m.put("status", j.getStatus() != null ? j.getStatus().name() : null);
            out.add(m);
        }
        return out;
    }

    /** 계정별 현황 — 누가 미리보기/구매를 몇 건 했는지 + 예상 원가. */
    @GetMapping("/users")
    public List<Map<String, Object>> users(Authentication auth) {
        adminGuard.require(auth);
        Map<String, long[]> agg = new java.util.HashMap<>();       // [미리보기, 구매, PDF, 하드커버, 이미지수]
        Map<String, String> providerOf = new java.util.HashMap<>();
        Map<String, LocalDateTime> lastAt = new java.util.HashMap<>();
        for (VideoJob j : videoJobRepository.findAll()) {
            String email = j.getRequesterEmail() != null ? j.getRequesterEmail() : "(비로그인)";
            long[] a = agg.computeIfAbsent(email, k -> new long[5]);
            a[0]++;
            if (j.getConfirmedAt() != null) {
                a[1]++;
                if (j.isPhysicalBookRequested()) a[3]++; else a[2]++;
            }
            a[4] += estimateImages(j);
            if (j.getRequesterProvider() != null) providerOf.put(email, j.getRequesterProvider());
            if (j.getCreatedAt() != null) {
                lastAt.merge(email, j.getCreatedAt(), (x, y) -> x.isAfter(y) ? x : y);
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, long[]> e : agg.entrySet()) {
            long[] a = e.getValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("email", e.getKey());
            m.put("provider", providerOf.get(e.getKey()));
            m.put("previews", a[0]);
            m.put("purchases", a[1]);
            m.put("pdf", a[2]);
            m.put("hardcover", a[3]);
            m.put("estCostKrw", a[4] * COST_PER_IMAGE_KRW);
            m.put("lastAt", lastAt.get(e.getKey()) != null ? lastAt.get(e.getKey()).format(DT) : null);
            out.add(m);
        }
        // 구매 많은 순 → 미리보기 많은 순
        out.sort((x, y) -> {
            int c = Long.compare((long) y.get("purchases"), (long) x.get("purchases"));
            return c != 0 ? c : Long.compare((long) y.get("previews"), (long) x.get("previews"));
        });
        return out;
    }

    /** 주문 단계 라벨: 확정 전=미리보기, 확정 후=구매유형. */
    private String stage(VideoJob j) {
        if (j.getConfirmedAt() == null) {
            return "미리보기";
        }
        return j.isPhysicalBookRequested() ? "하드커버구매" : "PDF구매";
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
