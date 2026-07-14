package com.storyai.backend.job;

import com.storyai.backend.domain.media.MediaAsset;
import com.storyai.backend.domain.media.MediaAssetRepository;
import com.storyai.backend.domain.media.MediaType;
import com.storyai.backend.domain.storycharacter.StoryCharacter;
import com.storyai.backend.domain.storycharacter.StoryCharacterRepository;
import com.storyai.backend.domain.videojob.OutputType;
import com.storyai.backend.domain.videojob.VideoJob;
import com.storyai.backend.domain.videojob.VideoJobRepository;
import com.storyai.backend.domain.videojob.WorkflowStep;
import com.storyai.backend.job.dto.CreateVideoJobRequest;
import com.storyai.backend.workflow.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VideoJobService {

    private static final Set<Integer> ALLOWED_BOOK_PAGES = Set.of(24, 36);
    private static final Set<Integer> ALLOWED_VIDEO_DURATIONS = Set.of(120, 300);

    private final VideoJobRepository videoJobRepository;
    private final StoryCharacterRepository storyCharacterRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final WorkflowEngine workflowEngine;

    @Transactional
    public VideoJob createJob(CreateVideoJobRequest request) {
        validate(request);

        String protagonist = request.characters().stream()
                .map(CreateVideoJobRequest.CharacterInput::name)
                .collect(Collectors.joining(", "));

        boolean isBook = request.outputType() == OutputType.BOOK;

        VideoJob job = VideoJob.builder()
                .outputType(request.outputType())
                .storyTheme(request.theme())
                .theme(request.theme().getLabel())
                .protagonistDescription(protagonist)
                .mood(request.mood())
                .ageGroup(request.ageGroup())
                .dedication(blankToNull(request.dedication()))
                .dedicationPhotoUrl(blankToNull(request.dedicationPhotoUrl()))
                .storyDirection(blankToNull(request.storyDirection()))
                .bookStyle(isBook ? request.bookStyle() : null)
                .bookPages(isBook ? request.bookPages() : null)
                .physicalBookRequested(isBook && request.physicalBookRequested())
                .videoStyle(isBook ? null : request.videoStyle())
                .videoDurationSec(isBook ? null : request.videoDurationSec())
                .targetLengthSeconds(isBook ? null : request.videoDurationSec())
                .currentStep(WorkflowStep.first())
                .build();
        job = videoJobRepository.save(job);

        for (CreateVideoJobRequest.CharacterInput c : request.characters()) {
            StoryCharacter character = StoryCharacter.builder()
                    .videoJob(job)
                    .name(c.name())
                    .role(c.role())
                    .photoUrls(List.copyOf(c.photoUrls()))
                    .build();
            storyCharacterRepository.save(character);
            job.getStoryCharacters().add(character);

            // 캐릭터 분석 단계가 참조하도록 모든 인물의 원본 사진을 MediaAsset으로도 등록.
            for (String photoUrl : c.photoUrls()) {
                MediaAsset photo = MediaAsset.builder()
                        .videoJob(job)
                        .type(MediaType.SOURCE_PHOTO)
                        .url(photoUrl)
                        .build();
                mediaAssetRepository.save(photo);
            }
        }

        // 커밋 이후 비동기로 워크플로우 첫 단계가 시작된다.
        workflowEngine.start(job.getId());
        return job;
    }

    @Transactional(readOnly = true)
    public VideoJob getJob(Long jobId) {
        VideoJob job = videoJobRepository.findById(jobId)
                .orElseThrow(() -> new VideoJobNotFoundException(jobId));
        job.getStoryCharacters().size(); // 응답 매핑 전 lazy 컬렉션 초기화
        return job;
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /** outputType에 따른 조건부 필수/범위 검증. 위반 시 IllegalArgumentException(→ 400). */
    private void validate(CreateVideoJobRequest request) {
        if (request.outputType() == OutputType.BOOK) {
            if (request.bookStyle() == null) {
                throw new IllegalArgumentException("책(BOOK)은 bookStyle이 필요합니다.");
            }
            if (request.bookPages() == null || !ALLOWED_BOOK_PAGES.contains(request.bookPages())) {
                throw new IllegalArgumentException("책 페이지 수는 24 또는 36이어야 합니다.");
            }
        } else {
            if (request.videoStyle() == null) {
                throw new IllegalArgumentException("영상(VIDEO)은 videoStyle이 필요합니다.");
            }
            if (request.videoDurationSec() == null || !ALLOWED_VIDEO_DURATIONS.contains(request.videoDurationSec())) {
                throw new IllegalArgumentException("영상 길이는 120초 또는 300초여야 합니다.");
            }
        }
    }
}
