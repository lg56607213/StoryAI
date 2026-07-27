package com.storyai.backend.payment;

import com.storyai.backend.domain.coupon.Coupon;
import com.storyai.backend.domain.coupon.CouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 쿠폰 검증·적용·사용처리. 100% 쿠폰이면 0원(무료 주문).
 * 시작 시 기본 쿠폰(테스트용 100% + 이벤트 예시)을 없으면 생성한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;

    /** 코드 정규화(대문자·공백 제거). */
    private String norm(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }

    /** 유효한 쿠폰이면 반환(활성·미만료·한도 이내). 아니면 empty. */
    public Optional<Coupon> validate(String code) {
        String c = norm(code);
        if (c.isEmpty()) {
            return Optional.empty();
        }
        return couponRepository.findByCode(c)
                .filter(cp -> cp.usableNow(LocalDateTime.now()));
    }

    /** 사용 처리(주문 확정 시 1회). 한도 관리를 위해 usedCount 증가. */
    @Transactional
    public void redeem(String code) {
        String c = norm(code);
        couponRepository.findByCode(c).ifPresent(cp -> {
            cp.setUsedCount(cp.getUsedCount() + 1);
            couponRepository.save(cp);
        });
    }

    /** 시작 시 기본 쿠폰을 없으면 생성(테스트 100% + 이벤트 예시). */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedDefaults() {
        ensure("FREE100", 100, "테스트용 100% 할인(0원)");
        ensure("WELCOME10", 10, "첫 구매 10% 할인(예시)");
        ensure("EVENT20", 20, "이벤트 20% 할인(예시)");
        ensure("SPECIAL30", 30, "특별 30% 할인(예시)");
    }

    private void ensure(String code, int percent, String memo) {
        if (couponRepository.findByCode(code).isEmpty()) {
            couponRepository.save(Coupon.builder()
                    .code(code).discountPercent(percent).active(true).memo(memo).build());
            log.info("쿠폰 생성: {} ({}%)", code, percent);
        }
    }
}
