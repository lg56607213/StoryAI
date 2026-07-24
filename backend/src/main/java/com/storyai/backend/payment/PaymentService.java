package com.storyai.backend.payment;

import com.storyai.backend.domain.payment.Payment;
import com.storyai.backend.domain.payment.PaymentRepository;
import com.storyai.backend.domain.payment.PaymentStatus;
import com.storyai.backend.domain.videojob.BookPhase;
import com.storyai.backend.domain.videojob.OutputType;
import com.storyai.backend.domain.videojob.VideoJob;
import com.storyai.backend.domain.videojob.VideoJobRepository;
import com.storyai.backend.domain.videojob.WorkflowStep;
import com.storyai.backend.pricing.Pricing;
import com.storyai.backend.workflow.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 결제 오케스트레이션(키움페이).
 * - prepare: 주문 금액을 서버에서 확정하고 해시를 받아 결제창 파라미터를 만든다.
 * - handleNotify: 통지URL로 온 결제 성공을 금액 검증·중복 방지 후 확정하고 전체 생성을 시작한다.
 * - cancel: 승인 취소.
 * 금액은 절대 클라이언트를 믿지 않고 Pricing으로 서버 계산한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final KiwoomPayClient kiwoom;
    private final PaymentRepository paymentRepository;
    private final VideoJobRepository videoJobRepository;
    private final WorkflowEngine workflowEngine;

    /** 결제 성공 후 고객이 돌아올 프론트 주소(성공/실패/취소). */
    @Value("${storyai.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    /** 결제 UI 노출 여부(provider=kiwoom). CPID 없어도 심사용 결제화면은 보여줄 수 있다. */
    public boolean isEnabled() {
        return kiwoom.uiEnabled();
    }

    /** 실제 결제 요청 가능(CPID까지 설정). */
    public boolean isReady() {
        return kiwoom.isConfigured();
    }

    /**
     * 결제창 요청용 파라미터를 만든다. 결제금액은 서버에서 계산(Pricing)한다.
     * 반환 맵: linkEncUrl + 결제창에 POST할 필드들.
     */
    @Transactional
    public Map<String, Object> prepare(Long jobId, String type) {
        VideoJob job = videoJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + jobId));
        if (job.getOutputType() != OutputType.BOOK) {
            throw new IllegalArgumentException("책 주문만 결제할 수 있습니다.");
        }
        Integer amount = Pricing.priceKrw(job);
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("결제 금액을 계산할 수 없습니다. 구매 유형·페이지 수를 확인해 주세요.");
        }
        if (!kiwoom.isConfigured()) {
            throw new IllegalStateException("결제가 아직 활성화되지 않았습니다. (PG 심사/설정 완료 후 가능)");
        }

        String orderNo = "TH" + jobId + "-" + shortStamp(); // 유일 주문번호
        String payMethod = kiwoom.cardMethod();
        String reqType = "P".equalsIgnoreCase(type) ? "P" : "M"; // 기본 모바일

        // 결제 레코드 생성(READY).
        Payment payment = Payment.builder()
                .videoJobId(jobId)
                .orderNo(orderNo)
                .amount(amount)
                .payMethod(payMethod)
                .status(PaymentStatus.READY)
                .build();
        paymentRepository.save(payment);

        String enc = kiwoom.requestHash(payMethod, reqType, orderNo, amount);

        // 결제창(2단계) POST 필드. HOME/FAIL/CLOSE는 결제창에서 이동할 프론트 주소.
        String base = frontendUrl.replaceAll("/+$", "");
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("PAYMETHOD", payMethod);
        fields.put("TYPE", reqType);
        fields.put("CPID", kiwoom.cpid());
        fields.put("ORDERNO", orderNo);
        fields.put("PRODUCTTYPE", job.isPhysicalBookRequested() ? "2" : "1"); // 2:실물 1:디지털
        fields.put("AMOUNT", String.valueOf(amount));
        fields.put("PRODUCTNAME", productName(job));
        fields.put("PRODUCTCODE", job.getPurchaseType() == null ? "BOOK" : job.getPurchaseType());
        fields.put("USERID", job.getRequesterEmail() == null ? ("job" + jobId) : job.getRequesterEmail());
        fields.put("USERNAME", job.getRecipientName() == null ? "" : job.getRecipientName());
        fields.put("EMAIL", job.getDeliveryEmail() == null ? "" : job.getDeliveryEmail());
        fields.put("TAXFREECD", "00"); // 과세(우리 가격은 VAT 포함)
        fields.put("KIWOOM_ENC", enc);
        fields.put("HOMEURL", base + "/pay/return?job=" + jobId);
        fields.put("FAILURL", base + "/pay/fail?job=" + jobId);
        fields.put("CLOSEURL", base + "/pay/cancel?job=" + jobId);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("action", kiwoom.linkEncUrl());
        out.put("fields", fields);
        out.put("orderNo", orderNo);
        out.put("amount", amount);
        return out;
    }

    /**
     * 통지URL: 결제 성공 통보 처리. 금액 검증 + 중복 방지 후 PAID로 바꾸고 전체 생성을 시작한다.
     * @return 정상 처리되면 true(호출부가 <RESULT>SUCCESS</RESULT> 응답).
     */
    @Transactional
    public boolean handleNotify(Map<String, String> params) {
        String orderNo = params.get("ORDERNO");
        String daoutrx = params.get("DAOUTRX");
        String amountStr = params.get("AMOUNT");
        if (orderNo == null || orderNo.isBlank()) {
            log.warn("결제 통지에 ORDERNO 없음: {}", params.keySet());
            return false;
        }
        Payment payment = paymentRepository.findByOrderNo(orderNo).orElse(null);
        if (payment == null) {
            log.warn("결제 통지: 알 수 없는 주문번호 {}", orderNo);
            return false;
        }
        // 재통지 중복 방지: 이미 PAID면 성공 응답만 하고 재처리하지 않는다.
        if (payment.getStatus() == PaymentStatus.PAID) {
            return true;
        }
        // 금액 검증(필수): 결제창 요청 금액과 통지 금액 일치 확인.
        int notified = parseInt(amountStr);
        if (notified != payment.getAmount()) {
            log.error("결제 금액 불일치 order={} 요청={} 통지={}", orderNo, payment.getAmount(), notified);
            payment.markFailed("금액 불일치: 요청 " + payment.getAmount() + " / 통지 " + notified);
            paymentRepository.save(payment);
            return false;
        }

        payment.markPaid(daoutrx, params.get("AUTHNO"), params.get("CARDNAME"), params.get("CARDCODE"));
        paymentRepository.save(payment);

        // 결제 완료 → 전체 생성 시작(미리보기 이후 PAGE_ILLUSTRATION부터).
        VideoJob job = videoJobRepository.findById(payment.getVideoJobId()).orElse(null);
        if (job != null && job.getBookPhase() != BookPhase.FULL) {
            job.setPaidAt(payment.getPaidAt());
            job.startFullGeneration(job.getPurchaseType(), job.getDeliveryEmail());
            videoJobRepository.save(job);
            workflowEngine.start(job.getId());
        }
        log.info("결제 승인 처리 완료 order={} daoutrx={} amount={}", orderNo, daoutrx, notified);
        return true;
    }

    /** 승인 취소(전체 또는 부분). */
    @Transactional
    public void cancel(Long jobId, Integer amount, String reason) {
        Payment payment = paymentRepository.findByVideoJobIdOrderByCreatedAtDesc(jobId).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PAID)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("취소할 결제 내역이 없습니다."));
        int cancelAmount = (amount == null || amount <= 0) ? payment.getAmount() : amount;
        kiwoom.cancel(payment.getPayMethod(), payment.getDaoutrx(), cancelAmount, reason);
        payment.markCanceled();
        paymentRepository.save(payment);
        log.info("결제 취소 완료 job={} daoutrx={} amount={}", jobId, payment.getDaoutrx(), cancelAmount);
    }

    // ---------- 내부 ----------

    private String productName(VideoJob job) {
        String title = job.getGeneratedTitle() != null ? job.getGeneratedTitle() : "우리 아이 동화책";
        String suffix = job.isPhysicalBookRequested() ? " (실물+PDF+영상)"
                : job.isVideoIncluded() ? " (PDF+영상)" : " (PDF)";
        String name = title + suffix;
        return name.length() > 50 ? name.substring(0, 50) : name;
    }

    private int parseInt(String s) {
        try {
            return s == null ? -1 : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** 짧은 유일 접미사(주문번호용). Date/random 미사용 환경 대비 nanoTime 기반. */
    private String shortStamp() {
        return Long.toString(System.nanoTime(), 36);
    }
}
