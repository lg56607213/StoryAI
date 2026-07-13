package com.storyai.backend.domain.videojob;

import com.storyai.backend.domain.characterprofile.CharacterProfile;
import com.storyai.backend.domain.media.MediaAsset;
import com.storyai.backend.domain.scene.Scene;
import com.storyai.backend.domain.storycharacter.StoryCharacter;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 영상 생성 요청 1건에 대한 파이프라인 상태를 나타내는 애그리거트 루트.
 * status/currentStep은 WorkflowStepExecutor를 통해서만 전이시킨다 (아래 마킹 메서드 참고).
 */
@Entity
@Table(name = "video_job")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 산출물 종류: 책 or 영상 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OutputType outputType = OutputType.VIDEO;

    /** 스토리 주제(공주/왕자/용사 등) */
    @Enumerated(EnumType.STRING)
    private StoryTheme storyTheme;

    /** 주제 라벨 (프롬프트/표시용, storyTheme.label과 동일) */
    @Column(nullable = false)
    private String theme;

    /** 등장인물 요약 (예: "지우, 서준") — 캐릭터 목록에서 파생 */
    @Column(nullable = false)
    private String protagonistDescription;

    /** 분위기 */
    private String mood;

    /** 목표 영상 길이(초) — VIDEO일 때 videoDurationSec와 동일하게 세팅 */
    private Integer targetLengthSeconds;

    /** 대상 연령대 (텍스트 분량·의성어 조절) */
    @Enumerated(EnumType.STRING)
    private AgeGroup ageGroup;

    /** 헌정 메세지 (선택) — 있으면 헌정 페이지로 삽입 */
    @Column(columnDefinition = "TEXT")
    private String dedication;

    /** 스토리 방향 (선택) — 고객이 이야기 틀을 잡아주는 자유 입력 */
    @Column(columnDefinition = "TEXT")
    private String storyDirection;

    // --- 책(BOOK) 선택 옵션 ---
    @Enumerated(EnumType.STRING)
    private BookStyle bookStyle;

    /** 책 페이지 수 (24 또는 36) */
    private Integer bookPages;

    /** 실물 책 배송 요청 여부 (요청 정보만 수집, 운영자가 수기 발주) */
    @Builder.Default
    private boolean physicalBookRequested = false;

    // --- 영상(VIDEO) 선택 옵션 ---
    @Enumerated(EnumType.STRING)
    private VideoStyle videoStyle;

    /** 영상 길이(초) (120 또는 300) */
    private Integer videoDurationSec;

    @Setter
    private String generatedTitle;

    @Setter
    @Column(columnDefinition = "TEXT")
    private String synopsis;

    @Setter
    @Column(length = 1000)
    private String resultVideoUrl;

    /** 최종 산출물 다운로드 URL (책=PDF 다운로드 엔드포인트, 영상=mp4). */
    @Setter
    @Column(length = 1000)
    private String resultUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private JobStatus status = JobStatus.PENDING;

    @Enumerated(EnumType.STRING)
    private WorkflowStep currentStep;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "videoJob", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StoryCharacter> storyCharacters = new ArrayList<>();

    @OneToOne(mappedBy = "videoJob", cascade = CascadeType.ALL, orphanRemoval = true)
    private CharacterProfile characterProfile;

    @OneToMany(mappedBy = "videoJob", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Scene> scenes = new ArrayList<>();

    @OneToMany(mappedBy = "videoJob", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MediaAsset> mediaAssets = new ArrayList<>();

    public void markRunning() {
        this.status = JobStatus.RUNNING;
    }

    public void markFailed(String reason) {
        this.status = JobStatus.FAILED;
        this.errorMessage = reason;
    }

    public void markCompleted() {
        this.status = JobStatus.COMPLETED;
    }

    public void moveToStep(WorkflowStep step) {
        this.currentStep = step;
    }
}
