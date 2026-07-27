package com.storyai.backend.domain.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 결제 1건(키움페이 통합결제 LINK). 주문(VideoJob) 1건당 한 건을 만든다.
 * 결제창 요청 시 READY로 만들고, 통지URL로 성공 통보가 오면 금액 검증 후 PAID로 바꾼다.
 * 취소되면 CANCELED. 실제 생성은 PAID가 되어야 시작한다.
 */
@Entity
@Table(name = "payment")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 대상 주문(VideoJob) id. */
    @Column(nullable = false)
    private Long videoJobId;

    /** 우리 주문번호(ORDERNO) — 키움페이 전송·통지 대조에 쓰는 유일 값. */
    @Column(nullable = false, unique = true, length = 50)
    private String orderNo;

    /** 결제 금액(원, VAT 포함, 쿠폰 할인 적용된 최종 금액). 통지 금액과 일치해야 승인 처리한다. */
    @Column(nullable = false)
    private int amount;

    /** 할인 전 원래 금액(원). 쿠폰 적용 기록용. */
    @Setter
    private Integer originalAmount;

    /** 적용된 쿠폰 코드(없으면 null). */
    @Setter
    @Column(length = 40)
    private String couponCode;

    /** 결제수단(PAYMETHOD) — CARD/CARDK 등. */
    @Column(length = 20)
    private String payMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.READY;

    /** 키움페이 다우거래번호(DAOUTRX) — 승인/취소의 유일 키. 재통지 중복 방지에 사용. */
    @Setter
    @Column(length = 30, unique = true)
    private String daoutrx;

    @Setter
    private String authNo;      // 카드 승인번호
    @Setter
    private String cardName;    // 카드사명
    @Setter
    @Column(length = 20)
    private String cardCode;

    @Setter
    private LocalDateTime paidAt;
    @Setter
    private LocalDateTime canceledAt;
    @Setter
    @Column(length = 500)
    private String failReason;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public void markPaid(String daoutrx, String authNo, String cardName, String cardCode) {
        this.status = PaymentStatus.PAID;
        this.daoutrx = daoutrx;
        this.authNo = authNo;
        this.cardName = cardName;
        this.cardCode = cardCode;
        this.paidAt = LocalDateTime.now();
    }

    public void markCanceled() {
        this.status = PaymentStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
    }

    public void markFailed(String reason) {
        this.status = PaymentStatus.FAILED;
        this.failReason = reason;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }
}
