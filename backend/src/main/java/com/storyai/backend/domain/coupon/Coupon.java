package com.storyai.backend.domain.coupon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 할인 쿠폰. 코드 입력 시 할인율(%)을 결제 금액에 적용한다.
 * discountPercent=100 이면 0원 → 결제창 없이 무료로 주문이 확정된다.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 고객이 입력하는 코드(대문자 정규화해 저장·조회). */
    @Column(unique = true, nullable = false, length = 40)
    private String code;

    /** 할인율(1~100). 100이면 전액 할인(0원). */
    @Column(nullable = false)
    private int discountPercent;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /** 최대 사용 횟수(null=무제한). */
    private Integer maxUses;

    @Column(nullable = false, columnDefinition = "int default 0")
    @Builder.Default
    private int usedCount = 0;

    /** 만료 시각(null=무기한). */
    private LocalDateTime expiresAt;

    /** 설명(관리용). */
    private String memo;

    @CreationTimestamp
    private LocalDateTime createdAt;

    /** 지금 사용 가능한가(활성·미만료·한도 이내). */
    public boolean usableNow(LocalDateTime now) {
        if (!active) {
            return false;
        }
        if (expiresAt != null && now.isAfter(expiresAt)) {
            return false;
        }
        return maxUses == null || usedCount < maxUses;
    }

    /** 이 쿠폰을 적용한 최종 금액(원). */
    public int applyTo(int amount) {
        int p = Math.max(0, Math.min(100, discountPercent));
        long discounted = (long) amount * (100 - p) / 100;
        return (int) Math.max(0, discounted);
    }
}
