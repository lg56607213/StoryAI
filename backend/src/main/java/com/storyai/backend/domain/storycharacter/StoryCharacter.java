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

    /** 주제 의상(공주 드레스 등) 캐릭터 시트 URL. 전환 이후 페이지 삽화가 참조. */
    @Setter
    @Column(length = 1000)
    private String characterSheetUrl;

    /** 아이 실제 옷을 그대로 살린 평상복 캐릭터 시트 URL. 표지·도입부 삽화가 참조("본인" 인식용). */
    @Setter
    @Column(length = 1000)
    private String everydaySheetUrl;
}
