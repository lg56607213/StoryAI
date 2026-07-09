package com.storyai.backend.workflow.handler;

import com.storyai.backend.domain.characterprofile.CharacterProfile;
import com.storyai.backend.domain.characterprofile.CharacterProfileRepository;
import com.storyai.backend.domain.media.MediaAssetRepository;
import com.storyai.backend.domain.media.MediaType;
import com.storyai.backend.domain.videojob.VideoJob;
import com.storyai.backend.domain.videojob.WorkflowStep;
import com.storyai.backend.workflow.WorkflowStepHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CharacterAnalysisStepHandler implements WorkflowStepHandler {

    private final CharacterProfileRepository characterProfileRepository;
    private final MediaAssetRepository mediaAssetRepository;

    @Override
    public WorkflowStep getStep() {
        return WorkflowStep.CHARACTER_ANALYSIS;
    }

    @Override
    public void execute(VideoJob job) {
        List<String> photoUrls = mediaAssetRepository
                .findByVideoJobIdAndType(job.getId(), MediaType.SOURCE_PHOTO)
                .stream()
                .map(asset -> asset.getUrl())
                .toList();

        // TODO: Character Engine(비전 모델) 연동 지점.
        // photoUrls를 분석해 얼굴특징/헤어스타일/표정/의상 등을 실제로 추출하도록 교체한다.
        CharacterProfile profile = CharacterProfile.builder()
                .videoJob(job)
                .sourcePhotoUrls(photoUrls)
                .faceFeatures("TBD")
                .hairStyle("TBD")
                .expression("TBD")
                .outfit("TBD")
                .personality("TBD")
                .age("TBD")
                .build();
        characterProfileRepository.save(profile);
    }
}
