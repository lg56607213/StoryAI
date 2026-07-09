package com.storyai.backend.domain.characterprofile;

import com.storyai.backend.domain.videojob.VideoJob;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * "Character Engine" 산출물 — 업로드된 사진에서 추출한 주인공의 특징을 저장하고,
 * 모든 장면의 이미지/영상 생성에서 동일하게 참조되어 캐릭터 일관성을 유지하는 데 쓰인다.
 */
@Entity
@Table(name = "character_profile")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CharacterProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_job_id", nullable = false, unique = true)
    private VideoJob videoJob;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "character_profile_source_photo", joinColumns = @JoinColumn(name = "character_profile_id"))
    @Column(name = "photo_url")
    @Builder.Default
    private List<String> sourcePhotoUrls = new ArrayList<>();

    @Setter
    private String faceFeatures;

    @Setter
    private String hairStyle;

    @Setter
    private String expression;

    @Setter
    private String outfit;

    @Setter
    private String personality;

    @Setter
    private String age;

    @Setter
    private String referenceImageUrl;
}
