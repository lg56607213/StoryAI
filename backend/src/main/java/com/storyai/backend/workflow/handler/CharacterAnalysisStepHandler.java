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
import com.storyai.backend.storage.StorageService;
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
    private final StorageService localStorage;

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

        // 책: 인물별 캐릭터 시트 2종(평상복=실제 옷 / 주제 의상) 생성. 키/사진 없으면 조용히 건너뜀.
        if (job.getOutputType() == OutputType.BOOK && imageGenerator.isAvailable()) {
            for (StoryCharacter character : storyCharacterRepository.findByVideoJobIdOrderByIdAsc(job.getId())) {
                generateSheets(job, character);
            }
        }
    }

    private void generateSheets(VideoJob job, StoryCharacter character) {
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
        String style = job.getBookStyle() != null ? job.getBookStyle().getGuide() : null;
        try {
            // 1) 평상복 시트 (실제 옷 보존) → 표지·도입부용
            boolean adult = character.getRole().isAdult();
            byte[] everyday = imageGenerator.everydaySheet(photos, character.getName(), style, adult);
            String everydayUrl = localStorage.storeGenerated(job.getId(), "sheet-everyday-" + character.getId() + ".png", everyday);
            character.setEverydaySheetUrl(everydayUrl);

            // 2) 주제 의상 시트 (평상복 얼굴 고정 → 주제에 맞는 옷) → 전환 이후용
            //    직접입력 주제면 그 문구에 어울리는 옷을 맡긴다.
            String costume = (job.getCustomTheme() != null && !job.getCustomTheme().isBlank())
                    ? com.storyai.backend.domain.videojob.StoryTheme.costumeForCustom(job.getCustomTheme())
                    : job.getStoryTheme().costumeFor(character.getRole());
            byte[] costumeImg = imageGenerator.costumeSheet(everyday, costume, style, adult);
            String costumeUrl = localStorage.storeGenerated(job.getId(), "sheet-costume-" + character.getId() + ".png", costumeImg);
            character.setCharacterSheetUrl(costumeUrl);

            storyCharacterRepository.save(character);
        } catch (Exception e) {
            log.warn("캐릭터 '{}' 시트 생성 실패: {}", character.getName(), e.getMessage());
        }
    }
}
