package com.storyai.backend.domain.bookpage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookPageRepository extends JpaRepository<BookPage, Long> {

    List<BookPage> findByVideoJobIdOrderByPageNumberAsc(Long videoJobId);
}
