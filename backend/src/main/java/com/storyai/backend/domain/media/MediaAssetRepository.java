package com.storyai.backend.domain.media;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

    List<MediaAsset> findByVideoJobIdAndType(Long videoJobId, MediaType type);
}
