package com.storyai.backend.job;

import com.storyai.backend.domain.media.MediaAsset;
import com.storyai.backend.domain.media.MediaAssetRepository;
import com.storyai.backend.domain.media.MediaType;
import com.storyai.backend.domain.storycharacter.StoryCharacter;
import com.storyai.backend.domain.storycharacter.StoryCharacterRepository;
import com.storyai.backend.domain.videojob.BookPhase;
import com.storyai.backend.domain.videojob.JobStatus;
import com.storyai.backend.domain.videojob.OutputType;
import com.storyai.backend.domain.videojob.StoryTheme;
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
    private final com.storyai.backend.auth.AdminGuard adminGuard;
    private final com.storyai.backend.payment.KiwoomPayClient kiwoomPayClient;
    private final com.storyai.backend.video.NarrationVideoService narrationVideoService;

    /** 로그인 계정당 하루 미리보기(생성) 제한. 0이면 무제한. */
    @org.springframework.beans.factory.annotation.Value("${storyai.rate-limit.previews-per-user-per-day:3}")
    private int previewsPerUserPerDay;

    @Transactional
    public VideoJob createJob(CreateVideoJobRequest request, Authentication auth) {
        validate(request);

        // 카카오는 이메일 동의항목이 없으면 이메일을 주지 않으므로 식별 키를 사용한다.
        String requesterEmail = LoginIdentity.identityOf(auth);
        enforceDailyLimit(requesterEmail, adminGuard.isAdmin(auth));

        // 관계를 함께 적어 이야기가 "엄마/아빠/동생"을 알맞게 다루도록 한다. 예: "소영(주인공), 지연(엄마)"
        String protagonist = request.characters().stream()
                .map(c -> {
                    String custom = blankToNull(c.customRole());
                    String role = (c.role() == com.storyai.backend.domain.storycharacter.CharacterRole.CUSTOM
                            && custom != null) ? custom : c.role().getLabel();
                    return c.name() + "(" + role + ")";
                })
                .collect(Collectors.joining(", "));

        boolean isBook = request.outputType() == OutputType.BOOK;

        // 주제: 직접입력이 있으면 그 문구를 쓰고, 의상·마스코트 기준이 될 enum은 선택값(없으면 모험)으로 둔다.
        String customTheme = blankToNull(request.customTheme());
        StoryTheme themeEnum = request.theme() != null ? request.theme() : StoryTheme.ADVENTURE;
        String themeLabel = customTheme != null ? customTheme : themeEnum.getLabel();

        VideoJob job = VideoJob.builder()
                .outputType(request.outputType())
                // 책은 줄거리 확인(OUTLINE)부터, 영상은 바로 전체 생성.
                .bookPhase(isBook ? BookPhase.OUTLINE : BookPhase.FULL)
                .storyTheme(themeEnum)
                .customTheme(customTheme)
                .theme(themeLabel)
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
                    .customRole(blankToNull(c.customRole()))
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
        job.getStoryCharacters().size(); // 응답 매핑(VideoJobResponse.from) 전 lazy 컬렉션 초기화(트랜잭션 내)
        // 구매 정보 저장(생성 시작과 분리 — 결제가 켜져 있으면 결제 후에 생성한다).
        String purchaseType = blankToNull(request.purchaseType());
        job.setPurchaseType(purchaseType);
        job.setDeliveryEmail(blankToNull(request.deliveryEmail()));
        // 구매 티어(PDF / PDF_VIDEO / PDF_VIDEO_BOOK) → 실물·영상 포함 여부. ("BOOK"=구버전 실물)
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

        if (kiwoomPayClient.uiEnabled()) {
            // 결제 활성화: 여기서 생성하지 않고 결제 대기 상태로 둔다(미리보기 단계 유지).
            // 결제 성공 통지가 오면 PaymentService가 전체 생성을 시작한다.
            videoJobRepository.save(job);
            return job;
        }

        // 결제 미설정(내부 테스트): 기존처럼 바로 전체 생성.
        job.startFullGeneration(purchaseType, job.getDeliveryEmail());
        videoJobRepository.save(job);
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
        // 복제된 목소리가 실제로 합성에 쓸 수 있는지 "지금 바로" 확인한다.
        // 실패하면 그 자리에서 다시 녹음하도록 명확한 오류를 돌려준다(나중에 메일로 처리하는 번거로움 방지).
        try {
            byte[] test = elevenLabs.textToSpeechPcm("안녕하세요.", voiceId);
            if (test == null || test.length == 0) {
                throw new IllegalStateException("빈 음성");
            }
        } catch (Exception e) {
            try {
                elevenLabs.deleteVoice(voiceId); // 쓸 수 없는 복제본은 즉시 정리(슬롯 낭비 방지)
            } catch (Exception ignore) {
                // 삭제 실패는 무시
            }
            throw new IllegalStateException(
                    "녹음하신 목소리를 처리하지 못했어요. 조용한 곳에서 30초 이상 또렷하게 다시 녹음해 주세요.");
        }
        job.setParentVoiceId(voiceId);
        job.setParentVoiceConsent(true);
        videoJobRepository.save(job);
        // 이미 완성된 영상 주문인데 (부모목소리 실패 등으로) 아직 영상이 없으면, 새 목소리로 영상 재생성.
        boolean awaitingVideo = job.getOutputType() == OutputType.BOOK
                && job.isVideoIncluded()
                && job.getStatus() == JobStatus.COMPLETED
                && job.getBookPhase() == BookPhase.FULL
                && (job.getNarrationVideoUrl() == null || job.getNarrationVideoUrl().isBlank());
        if (awaitingVideo) {
            narrationVideoService.generateAsync(jobId);
        }
        job.getStoryCharacters().size(); // 응답 매핑(VideoJobResponse.from) 전 lazy 컬렉션 초기화
        return job;
    }

    /**
     * 줄거리 확인 단계에서 고객 수정 요청을 반영해 줄거리를 다시 생성한다(그림 생성 전, 비용 없음).
     * 비동기로 STORY_GENERATION만 다시 실행하고 OUTLINE 단계에서 다시 멈춘다.
     */
    @Transactional
    public VideoJob reviseOutline(Long jobId, String feedback) {
        VideoJob job = requireOutlineStage(jobId);
        job.setOutlineFeedback(blankToNull(feedback));
        job.moveToStep(WorkflowStep.STORY_GENERATION);
        job.markRunning();
        videoJobRepository.save(job);
        workflowEngine.start(jobId);
        job.getStoryCharacters().size(); // 응답 매핑(VideoJobResponse.from) 전 lazy 컬렉션 초기화
        return job;
    }

    /**
     * 줄거리를 확정하고 미리보기 생성으로 넘어간다. editedSynopsis가 있으면 고객이 직접 고친 줄거리를 그대로 쓴다.
     */
    @Transactional
    public VideoJob approveOutline(Long jobId, String editedTitle, String editedSynopsis) {
        VideoJob job = requireOutlineStage(jobId);
        String synopsis = blankToNull(editedSynopsis);
        if (synopsis != null) {
            job.setSynopsis(synopsis);
        }
        String title = blankToNull(editedTitle);
        if (title != null) {
            job.setGeneratedTitle(title);
        }
        // 미리보기 단계로 전환: 줄거리는 이미 있으니 PAGE_PLANNING부터 이어간다.
        job.moveToPreviewPhase();
        job.moveToStep(WorkflowStep.PAGE_PLANNING);
        job.markRunning();
        videoJobRepository.save(job);
        workflowEngine.start(jobId);
        job.getStoryCharacters().size(); // 응답 매핑(VideoJobResponse.from) 전 lazy 컬렉션 초기화
        return job;
    }

    private VideoJob requireOutlineStage(Long jobId) {
        VideoJob job = videoJobRepository.findById(jobId)
                .orElseThrow(() -> new VideoJobNotFoundException(jobId));
        if (job.getOutputType() != OutputType.BOOK || job.getBookPhase() != BookPhase.OUTLINE) {
            throw new IllegalArgumentException("줄거리 확인 단계의 주문만 처리할 수 있습니다.");
        }
        return job;
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
     * - 비로그인은 IP 기반 RateLimitFilter가 담당하므로 여기서는 통과.
     * - 관리자는 운영·테스트를 위해 제한 없음.
     * - 실패한 건은 사용자 탓이 아니므로 한도에서 제외한다.
     */
    private void enforceDailyLimit(String requesterEmail, boolean admin) {
        if (admin || previewsPerUserPerDay <= 0 || requesterEmail == null || requesterEmail.isBlank()) {
            return;
        }
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long todayCount = videoJobRepository.countByRequesterEmailAndCreatedAtAfterAndStatusNot(
                requesterEmail, todayStart, JobStatus.FAILED);
        if (todayCount >= previewsPerUserPerDay) {
            throw new IllegalArgumentException(
                    "오늘은 동화를 " + todayCount + "권 만드셨어요. 하루 " + previewsPerUserPerDay
                            + "권까지 만들 수 있어요. 내일 다시 시도해 주세요.");
        }
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /** outputType에 따른 조건부 필수/범위 검증. 위반 시 IllegalArgumentException(→ 400). */
    private void validate(CreateVideoJobRequest request) {
        // 주제는 목록 선택 또는 직접입력 중 하나가 반드시 있어야 한다.
        if (request.theme() == null && blankToNull(request.customTheme()) == null) {
            throw new IllegalArgumentException("주제를 선택하거나 직접 입력해 주세요.");
        }
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
