package com.storyai.backend.workflow;

import com.storyai.backend.domain.characterprofile.CharacterProfileRepository;
import com.storyai.backend.domain.scene.SceneRepository;
import com.storyai.backend.domain.storycharacter.CharacterRole;
import com.storyai.backend.domain.videojob.AgeGroup;
import com.storyai.backend.domain.videojob.JobStatus;
import com.storyai.backend.domain.videojob.OutputType;
import com.storyai.backend.domain.videojob.StoryTheme;
import com.storyai.backend.domain.videojob.VideoJob;
import com.storyai.backend.domain.videojob.VideoJobRepository;
import com.storyai.backend.domain.videojob.VideoStyle;
import com.storyai.backend.domain.videojob.WorkflowStep;
import com.storyai.backend.job.VideoJobService;
import com.storyai.backend.job.dto.CreateVideoJobRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
class WorkflowEngineIntegrationTest {

    @Autowired
    private VideoJobService videoJobService;

    @Autowired
    private VideoJobRepository videoJobRepository;

    @Autowired
    private SceneRepository sceneRepository;

    @Autowired
    private CharacterProfileRepository characterProfileRepository;

    @Test
    void runsAllStepsToCompletion() {
        var request = new CreateVideoJobRequest(
                OutputType.VIDEO,
                StoryTheme.SPACE,
                AgeGroup.AGE_5_6,
                "신나는",
                null,
                null,
                null,
                null,
                false,
                VideoStyle.ANIM_2D,
                120,
                List.of(new CreateVideoJobRequest.CharacterInput(
                        "지우",
                        CharacterRole.MAIN,
                        List.of("https://example.com/photo1.jpg", "https://example.com/photo2.jpg")
                ))
        );

        VideoJob created = videoJobService.createJob(request);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            VideoJob job = videoJobRepository.findById(created.getId()).orElseThrow();
            assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
            assertThat(job.getCurrentStep()).isEqualTo(WorkflowStep.VIDEO_COMPOSITION);
            assertThat(job.getResultVideoUrl()).isNotBlank();
            assertThat(job.getGeneratedTitle()).isNotBlank();
        });

        var scenes = sceneRepository.findByVideoJobIdOrderBySceneNumberAsc(created.getId());
        assertThat(scenes).isNotEmpty();
        assertThat(scenes).allSatisfy(scene -> {
            assertThat(scene.getImageUrl()).isNotBlank();
            assertThat(scene.getVideoUrl()).isNotBlank();
            assertThat(scene.getVoiceAudioUrl()).isNotBlank();
            assertThat(scene.getSubtitleText()).isNotBlank();
        });

        var characterProfile = characterProfileRepository.findByVideoJobId(created.getId());
        assertThat(characterProfile).isPresent();
        assertThat(characterProfile.get().getSourcePhotoUrls()).hasSize(2);
    }
}
