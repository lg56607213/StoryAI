package com.storyai.backend.job;

import com.storyai.backend.domain.videojob.OutputType;
import com.storyai.backend.domain.videojob.VideoJob;
import com.storyai.backend.job.dto.ConfirmVideoJobRequest;
import com.storyai.backend.job.dto.CreateVideoJobRequest;
import com.storyai.backend.job.dto.VideoJobResponse;
import com.storyai.backend.storage.LocalStorage;
import com.storyai.backend.video.NarrationVideoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;

@RestController
@RequestMapping("/api/video-jobs")
@RequiredArgsConstructor
public class VideoJobController {

    private final VideoJobService videoJobService;
    private final LocalStorage localStorage;
    private final NarrationVideoService narrationVideoService;

    @PostMapping
    public ResponseEntity<VideoJobResponse> create(@Valid @RequestBody CreateVideoJobRequest request,
                                                   org.springframework.security.core.Authentication auth) {
        var job = videoJobService.createJob(request, auth);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(VideoJobResponse.from(job));
    }

    @GetMapping("/{id}")
    public VideoJobResponse get(@PathVariable Long id) {
        return VideoJobResponse.from(videoJobService.getJob(id));
    }

    /** 미리보기 확정 → 전체 생성 시작(구매유형·이메일 수집, 결제는 이후). */
    @PostMapping("/{id}/confirm")
    public VideoJobResponse confirm(@PathVariable Long id, @RequestBody ConfirmVideoJobRequest request,
                                    org.springframework.security.core.Authentication auth) {
        return VideoJobResponse.from(videoJobService.confirmFull(id, request, auth));
    }

    /**
     * 낭독 영상(mp4) 생성 트리거. 완성된 책의 페이지 삽화 + 세그먼트를 목소리로 읽어 영상으로 만든다.
     * 비동기로 시작하고 즉시 현재 상태를 반환한다(narrationVideoStatus=generating).
     * 완료되면 narrationVideoUrl 이 채워지고 status=ready 가 된다.
     */
    @PostMapping("/{id}/narration-video")
    public VideoJobResponse startNarrationVideo(@PathVariable Long id) {
        VideoJob job = videoJobService.getJob(id);
        if (!job.isVideoIncluded()) {
            throw new IllegalArgumentException("영상이 포함된 구매(PDF+영상)에서만 만들 수 있어요.");
        }
        narrationVideoService.generateAsync(id);
        return VideoJobResponse.from(job);
    }

    /**
     * 부모 목소리 등록 — 녹음 파일을 올리면 음성을 복제해 이 주문에 연결한다.
     * 이후 낭독 영상의 "서술" 부분이 부모 목소리로 읽힌다(대사는 캐릭터 목소리 유지).
     * consent=true(목소리 복제·이용 동의) 필수.
     */
    @PostMapping("/{id}/parent-voice")
    public VideoJobResponse registerParentVoice(
            @PathVariable Long id,
            // required=false: 파일이 없으면 Spring 예외(500) 대신 아래에서 명확한 400으로 처리한다.
            @RequestParam(value = "file", required = false) org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "consent", defaultValue = "false") boolean consent) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("녹음 파일이 비어 있습니다.");
        }
        try {
            return VideoJobResponse.from(
                    videoJobService.registerParentVoice(id, file.getBytes(), file.getOriginalFilename(), consent));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("녹음 파일을 읽지 못했습니다: " + e.getMessage(), e);
        }
    }

    /** 완성된 책 PDF 다운로드. (영상 다운로드는 Slice 4에서) */
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        VideoJob job = videoJobService.getJob(id);
        if (job.getOutputType() != OutputType.BOOK) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        Path pdf = localStorage.bookPdfPath(id);
        if (!localStorage.exists(pdf)) {
            // 아직 생성 중이거나 실패
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        byte[] bytes = localStorage.read(pdf);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"storybook-" + id + ".pdf\"")
                .body(bytes);
    }
}
