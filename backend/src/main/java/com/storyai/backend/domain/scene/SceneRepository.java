package com.storyai.backend.domain.scene;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SceneRepository extends JpaRepository<Scene, Long> {

    List<Scene> findByVideoJobIdOrderBySceneNumberAsc(Long videoJobId);
}
