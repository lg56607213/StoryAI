package com.storyai.backend.domain.bookpage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookPageRepository extends JpaRepository<BookPage, Long> {

    List<BookPage> findByVideoJobIdOrderByPageNumberAsc(Long videoJobId);

    /** 페이지 구성 단계 재실행 시 중복 생성을 막기 위해 기존 페이지를 지운다. */
    void deleteByVideoJobId(Long videoJobId);
}
