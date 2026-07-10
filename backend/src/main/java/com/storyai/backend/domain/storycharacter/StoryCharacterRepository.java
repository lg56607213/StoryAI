package com.storyai.backend.domain.storycharacter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoryCharacterRepository extends JpaRepository<StoryCharacter, Long> {

    List<StoryCharacter> findByVideoJobIdOrderByIdAsc(Long videoJobId);
}
