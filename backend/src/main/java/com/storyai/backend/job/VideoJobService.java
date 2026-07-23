package com.storyai.backend.job;

import com.storyai.backend.domain.media.MediaAsset;
import com.storyai.backend.domain.media.MediaAssetRepository;
import com.storyai.backend.domain.media.MediaType;
import com.storyai.backend.domain.storycharacter.StoryCharacter;
import com.storyai.backend.domain.storycharacter.StoryCharacterRepository;
import com.storyai.backend.domain.videojob.BookPhase;
import com.storyai.backend.domain.videojob.OutputType;
import com.storyai.backend.job.dto.ConfirmVideoJobRequest;
import com.storyai.backend.domain.videojob.VideoJob;
import com.storyai.backend.domain.videojob.VideoJobRepository;
import com.storyai.backend.domain.videojob.WorkflowStep;
import com.storyai.backend.job.dto.CreateVideoJobRequest;
import com.storyai.backend.auth.LoginIdentity;
import com.storyai.backend.workflow.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final com.storyai.backend.ai.voice.ElevenLabsClient elevenLabs;

    /** 로그인 계정당 하루 미리보기(생성) 제한. 0이면 무제한. */
    @org.springframework.beans.factory.annotation.Value("${storyai.rate-limit.previews-per-user-per-day:3}")
    private int previewsPerUserPerDay;

    @Transactional
    public VideoJob createJob(CreateVideoJobRequest request, Authentication auth) {
        validate(request);

        // 카카오는 이메일 동의항목이 없으면 이메일을 주지 않으므로 식별 키를 사용한다.
        String requesterEmail = LoginIdentity.identityOf(auth);
        enforceDailyLimit(requesterEmail);

        String protagonist = request.characters().stream()
                .map(CreateVideoJobRequest.CharacterInput::name)
                .collect(Collectors.joining(", "));

        boolean isBook = request.outputType() == OutputType.BOOK;

        VideoJob job = VideoJob.builder()
                .outputType(request.outputType())
                // 책은 미리보기부터, 영상은 바로 전체 생성.
                .bookPhase(isBook ? BookPhase.PREVIEW : BookPhase.FULL)
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
        // 로그인 상태면 요청 계정을 기록(관리자 조회·마이페이지용).
        job.setRequesterEmail(requesterEmail);
        job.setRequesterProvider(LoginIdentity.providerOf(auth));
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

    /** 미리보기 확정 → 전체 생성 재개. 구매유형·이메일을 저장하고 삽화 단계부터 워크플로우를 다시 태운다. */
    @Transactional
    public VideoJob confirmFull(Long jobId, ConfirmVideoJobRequest request, Authentication auth) {
        VideoJob job = videoJobRepository.findById(jobId)
                .orElseThrow(() -> new VideoJobNotFoundException(jobId));
        if (job.getOutputType() != OutputType.BOOK) {
            throw new IllegalArgumentException("책 주문만 확정할 수 있습니다.");
        }
        if (job.getBookPhase() == BookPhase.FULL) {
            throw new IllegalArgumentException("이미 전체 생성이 진행/완료된 주문입니다.");
        }
        String purchaseType = blankToNull(request.purchaseType());
        job.startFullGeneration(purchaseType, blankToNull(request.deliveryEmail()));
        // 구매 티어(PDF / PDF_VIDEO / PDF_VIDEO_BOOK) → 실물·영상 포함 여부 세팅. ("BOOK"=구버전 실물)
        boolean physical = "PDF_VIDEO_BOOK".equals(purchaseType) || "BOOK".equals(purchaseType);
        boolean video = purchaseType != null && purchaseType.contains("VIDEO");
        job.setPhysicalBookRequested(physical);
        job.setVideoIncluded(video);
        // 실물 책 배송 정보(책자 구매 시).
        job.setRecipientName(blankToNull(request.recipientName()));
        job.setRecipientPhone(blankToNull(request.recipientPhone()));
        job.setPostalCode(blankToNull(request.postalCode()));
        job.setShippingAddress(blankToNull(request.shippingAddress()));
        job.setShippingAddressDetail(blankToNull(request.shippingAddressDetail()));
        // 구매요청(확정) 시점과 요청 계정 기록.
        job.setConfirmedAt(LocalDateTime.now());
        String identity = LoginIdentity.identityOf(auth);
        if (identity != null) {
            job.setRequesterEmail(identity);
            job.setRequesterProvider(LoginIdentity.providerOf(auth));
        }
        videoJobRepository.save(job);
        // 커밋 이후 PAGE_ILLUSTRATION부터 재개된다.
        workflowEngine.start(job.getId());
        return job;
    }

    /**
     * 부모 목소리 등록: 녹음 샘플로 음성을 복제해 jobId에 연결한다.
     * 음성은 생체정보에 준하므로 명시적 동의(consent)가 없으면 거부한다.
     */
    @Transactional
    public VideoJob registerParentVoice(Long jobId, byte[] audio, String filename, boolean consent) {
        VideoJob job = videoJobRepository.findById(jobId)
                .orElseThrow(() -> new VideoJobNotFoundException(jobId));
        if (!consent) {
            throw new IllegalArgumentException("목소리 복제·이용에 대한 동의가 필요합니다.");
        }
        if (!elevenLabs.isConfigured()) {
            throw new IllegalStateException("부모 목소리 기능이 아직 활성화되지 않았습니다.");
        }
        if (audio == null || audio.length < 20_000) {
            throw new IllegalArgumentException("녹음이 너무 짧아요. 30초 이상 또렷하게 읽어 주세요.");
        }
        // 기존 복제본이 있으면 정리(중복 슬롯 방지).
        if (job.getParentVoiceId() != null && !job.getParentVoiceId().isBlank()) {
            elevenLabs.deleteVoice(job.getParentVoiceId());
        }
        String voiceId = elevenLabs.cloneVoice("todayhero-job-" + jobId, audio, filename);
        job.setParentVoiceId(voiceId);
        job.setParentVoiceConsent(true);
        return videoJobRepository.save(job);
    }

    @Transactional(readOnly = true)
    public VideoJob getJob(Long jobId) {
        VideoJob job = videoJobRepository.findById(jobId)
                .orElseThrow(() -> new VideoJobNotFoundException(jobId));
        job.getStoryCharacters().size(); // 응답 매핑 전 lazy 컬렉션 초기화
        return job;
    }

    /**
     * 로그인 계정당 하루 생성 횟수를 제한한다(비용 보호).
     * 비로그인은 IP 기반 RateLimitFilter가 담당하므로 여기서는 통과시킨다.
     */
    private void enforceDailyLimit(String requesterEmail) {
        if (previewsPerUserPerDay <= 0 || requesterEmail == null || requesterEmail.isBlank()) {
            return;
        }
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long todayCount = videoJobRepository.countByRequesterEmailAndCreatedAtAfter(requesterEmail, todayStart);
        if (todayCount >= previewsPerUserPerDay) {
            throw new IllegalArgumentException(
                    "하루에 만들 수 있는 동화는 " + previewsPerUserPerDay + "권까지예요. 내일 다시 시도해 주세요.");
        }
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
