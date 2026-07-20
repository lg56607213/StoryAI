package com.storyai.backend.domain.review;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** 노출용: 숨김 아닌 최신 후기. */
    List<Review> findTop60ByHiddenFalseOrderByCreatedAtDesc();

    long countByHiddenFalse();
}
