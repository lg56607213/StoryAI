package com.storyai.backend.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 결제(키움페이) 엔드포인트.
 * - GET  /api/pay/status              : 결제 사용 가능 여부
 * - POST /api/pay/kiwoom/prepare/{id} : 결제창 파라미터(해시 포함) 발급
 * - POST /api/pay/kiwoom/notify       : 결제 결과 통지(키움페이 서버 → 우리). 성공 시 <RESULT>SUCCESS</RESULT>
 * - POST /api/pay/kiwoom/cancel/{id}  : 승인 취소(관리자/고객)
 */
@Slf4j
@RestController
@RequestMapping("/api/pay")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "enabled", paymentService.isEnabled(), // 결제 화면 노출
                "ready", paymentService.isReady());    // 실제 결제 가능(CPID 설정됨)
    }

    /** 결제창을 열기 위한 파라미터를 발급한다(금액은 서버에서 확정, 쿠폰 적용). */
    @PostMapping("/kiwoom/prepare/{id}")
    public Map<String, Object> prepare(@PathVariable Long id,
                                       @RequestParam(name = "type", defaultValue = "M") String type,
                                       @RequestParam(name = "coupon", required = false) String coupon) {
        return paymentService.prepare(id, type, coupon);
    }

    /** 쿠폰 확인: 이 주문에 쿠폰 적용 시 할인율·최종금액을 미리 알려준다. */
    @PostMapping("/coupon/check/{id}")
    public Map<String, Object> checkCoupon(@PathVariable Long id, @RequestBody CouponRequest req) {
        return paymentService.checkCoupon(id, req == null ? null : req.code());
    }

    /** 무료 주문(0원): 100% 쿠폰 등으로 최종 0원일 때만 결제 없이 전체 생성 시작. */
    @PostMapping("/free/{id}")
    public com.storyai.backend.job.dto.VideoJobResponse free(@PathVariable Long id,
                                                             @RequestBody CouponRequest req) {
        return com.storyai.backend.job.dto.VideoJobResponse.from(
                paymentService.freeOrder(id, req == null ? null : req.code()));
    }

    public record CouponRequest(String code) {
    }

    /**
     * 결제 결과 통지(키움페이 통신서버가 호출). 폼 파라미터로 결과가 전달된다.
     * 금액 검증·중복 방지 후 성공이면 반드시 <RESULT>SUCCESS</RESULT>를 응답해야 정상 거래로 인지된다.
     */
    @PostMapping(value = "/kiwoom/notify", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> notify(HttpServletRequest request) {
        Map<String, String> params = new LinkedHashMap<>();
        request.getParameterMap().forEach((k, v) -> params.put(k, v.length > 0 ? v[0] : ""));
        boolean ok;
        try {
            ok = paymentService.handleNotify(params);
        } catch (Exception e) {
            log.error("결제 통지 처리 오류: {}", e.getMessage(), e);
            ok = false;
        }
        String body = ok
                ? "<html><body><RESULT>SUCCESS</RESULT></body></html>"
                : "<html><body><RESULT>FAIL</RESULT></body></html>";
        return ResponseEntity.ok(body);
    }

    /** 승인 취소. amount 미지정 시 전액 취소. */
    @PostMapping("/kiwoom/cancel/{id}")
    public Map<String, Object> cancel(@PathVariable Long id, @RequestBody(required = false) CancelRequest req) {
        Integer amount = req != null ? req.amount() : null;
        String reason = req != null ? req.reason() : null;
        paymentService.cancel(id, amount, reason);
        return Map.of("canceled", true);
    }

    public record CancelRequest(Integer amount, String reason) {
    }
}
