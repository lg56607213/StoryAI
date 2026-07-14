package com.storyai.backend.job.dto;

/**
 * 미리보기 확정 요청. 구매 유형(PDF/BOOK)과 완성본을 받을 이메일(선택)을 담는다.
 * 결제는 이후 단계에서 붙인다(지금은 정보만 수집).
 */
public record ConfirmVideoJobRequest(
        String purchaseType,   // "PDF" 또는 "BOOK"
        String deliveryEmail
) {
}
