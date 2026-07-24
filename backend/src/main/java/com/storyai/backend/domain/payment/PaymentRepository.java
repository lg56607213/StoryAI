package com.storyai.backend.domain.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderNo(String orderNo);

    Optional<Payment> findByDaoutrx(String daoutrx);

    /** 주문(VideoJob)의 결제들(최신순) — 보통 1건. */
    List<Payment> findByVideoJobIdOrderByCreatedAtDesc(Long videoJobId);
}
