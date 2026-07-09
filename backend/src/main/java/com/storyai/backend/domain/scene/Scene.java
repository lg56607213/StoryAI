package com.storyai.backend.domain.scene;

import com.storyai.backend.domain.videojob.VideoJob;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "scene")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Scene {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_job_id", nullable = false)
    private VideoJob videoJob;

    @Column(nullable = false)
    private Integer sceneNumber;

    @Setter
    @Column(columnDefinition = "TEXT")
    private String script;

    @Setter
    @Column(columnDefinition = "TEXT")
    private String narration;

    @Setter
    @Column(columnDefinition = "TEXT")
    private String sceneDescription;

    @Setter
    private String imageUrl;

    @Setter
    private String videoUrl;

    @Setter
    private String voiceAudioUrl;

    @Setter
    @Column(columnDefinition = "TEXT")
    private String subtitleText;
}
