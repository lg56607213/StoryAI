package com.storyai.backend.domain.characterprofile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CharacterProfileRepository extends JpaRepository<CharacterProfile, Long> {

    Optional<CharacterProfile> findByVideoJobId(Long videoJobId);
}
