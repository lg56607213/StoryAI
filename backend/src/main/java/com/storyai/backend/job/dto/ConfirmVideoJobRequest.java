package com.storyai.backend.job.dto;

/**
 * 미리보기 확정 요청. 구매 티어와 완성본을 받을 이메일(선택)을 담는다.
 * 결제는 이후 단계에서 붙인다(지금은 정보만 수집).
 */
public record ConfirmVideoJobRequest(
        String purchaseType,   // "PDF" | "PDF_VIDEO" | "PDF_VIDEO_BOOK" (구버전 "BOOK"=실물)
        String deliveryEmail,
        // 실물(BOOK) 배송 정보 — 책자 구매 시 입력
        String recipientName,
        String recipientPhone,
        String postalCode,
        String shippingAddress,
        String shippingAddressDetail
) {
}
