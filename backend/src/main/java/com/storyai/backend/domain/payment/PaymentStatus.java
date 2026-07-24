package com.storyai.backend.domain.payment;

/** 결제 상태. READY(결제창 요청) → PAID(통지 성공) 또는 FAILED, 이후 CANCELED. */
public enum PaymentStatus {
    READY,
    PAID,
    FAILED,
    CANCELED
}
