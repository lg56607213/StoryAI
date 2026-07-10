package com.storyai.backend.domain.storycharacter;

import com.storyai.backend.domain.videojob.VideoJob;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 스토리에 등장시킬 입력 인물(자녀/둘째/친구). 고객이 등록한 원본이며,
 * 여기 사진들을 근거로 CharacterProfile(일관성 참조)이 생성된다.
 */
@Entity
@Table(name = "story_character")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryCharacter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_job_id", nullable = false)
    private VideoJob videoJob;

    /** 인물 이름 (예: "지우") */
    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CharacterRole role;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "story_character_photo", joinColumns = @JoinColumn(name = "story_character_id"))
    @Column(name = "photo_url")
    @Builder.Default
    private List<String> photoUrls = new ArrayList<>();

    /** 업로드 사진으로 생성한 캐릭터 시트(참조 이미지) URL. 모든 페이지 삽화가 이걸 참조해 일관성 유지. */
    @Setter
    @Column(length = 1000)
    private String characterSheetUrl;
}
