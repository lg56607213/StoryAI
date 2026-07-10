package com.storyai.backend.workflow.handler;

import com.storyai.backend.ai.image.ImageGenerator;
import com.storyai.backend.domain.characterprofile.CharacterProfile;
import com.storyai.backend.domain.characterprofile.CharacterProfileRepository;
import com.storyai.backend.domain.media.MediaAssetRepository;
import com.storyai.backend.domain.media.MediaType;
import com.storyai.backend.domain.storycharacter.StoryCharacter;
import com.storyai.backend.domain.storycharacter.StoryCharacterRepository;
import com.storyai.backend.domain.videojob.OutputType;
import com.storyai.backend.domain.videojob.VideoJob;
import com.storyai.backend.domain.videojob.WorkflowStep;
import com.storyai.backend.storage.LocalStorage;
import com.storyai.backend.workflow.WorkflowStepHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CharacterAnalysisStepHandler implements WorkflowStepHandler {

    private final CharacterProfileRepository characterProfileRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final StoryCharacterRepository storyCharacterRepository;
    private final ImageGenerator imageGenerator;
    private final LocalStorage localStorage;

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

        // 기존: 전체 사진 기반 CharacterProfile (영상 파이프라인 및 하위 호환)
        CharacterProfile profile = CharacterProfile.builder()
                .videoJob(job)
                .sourcePhotoUrls(photoUrls)
                .faceFeatures("TBD").hairStyle("TBD").expression("TBD")
                .outfit("TBD").personality("TBD").age("TBD")
                .build();
        characterProfileRepository.save(profile);

        // 책: 인물별 파스텔 캐릭터 시트 생성 (참조 이미지). 키/사진 없으면 조용히 건너뜀.
        if (job.getOutputType() == OutputType.BOOK && imageGenerator.isAvailable()) {
            for (StoryCharacter character : storyCharacterRepository.findByVideoJobIdOrderByIdAsc(job.getId())) {
                generateSheet(job, character);
            }
        }
    }

    private void generateSheet(VideoJob job, StoryCharacter character) {
        List<byte[]> photos = new ArrayList<>();
        for (String url : character.getPhotoUrls()) {
            byte[] bytes = localStorage.loadByUrl(url);
            if (bytes != null) {
                photos.add(bytes);
            }
        }
        if (photos.isEmpty()) {
            log.warn("캐릭터 '{}' 사진 바이트를 찾을 수 없어 시트 생성 건너뜀", character.getName());
            return;
        }
        try {
            byte[] sheet = imageGenerator.characterSheet(photos, character.getRole(), character.getName());
            String url = localStorage.storeGenerated(job.getId(), "sheet-" + character.getId() + ".png", sheet);
            character.setCharacterSheetUrl(url);
            storyCharacterRepository.save(character);
        } catch (Exception e) {
            log.warn("캐릭터 '{}' 시트 생성 실패: {}", character.getName(), e.getMessage());
        }
    }
}
