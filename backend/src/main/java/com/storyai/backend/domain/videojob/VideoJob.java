package com.storyai.backend.domain.videojob;

import com.storyai.backend.domain.characterprofile.CharacterProfile;
import com.storyai.backend.domain.media.MediaAsset;
import com.storyai.backend.domain.scene.Scene;
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

    /** 주제 */
    @Column(nullable = false)
    private String theme;

    /** 등장인물 설명 (예: "5살 딸 지우") */
    @Column(nullable = false)
    private String protagonistDescription;

    /** 분위기 */
    private String mood;

    /** 목표 영상 길이(초) */
    private Integer targetLengthSeconds;

    /** 대상 연령 */
    private String targetAgeGroup;

    @Setter
    private String generatedTitle;

    @Setter
    @Column(columnDefinition = "TEXT")
    private String synopsis;

    @Setter
    @Column(length = 1000)
    private String resultVideoUrl;

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
