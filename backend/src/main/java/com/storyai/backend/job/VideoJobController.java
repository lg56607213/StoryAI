package com.storyai.backend.job;

import com.storyai.backend.domain.videojob.OutputType;
import com.storyai.backend.domain.videojob.VideoJob;
import com.storyai.backend.job.dto.CreateVideoJobRequest;
import com.storyai.backend.job.dto.VideoJobResponse;
import com.storyai.backend.storage.LocalStorage;
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
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;

@RestController
@RequestMapping("/api/video-jobs")
@RequiredArgsConstructor
public class VideoJobController {

    private final VideoJobService videoJobService;
    private final LocalStorage localStorage;

    @PostMapping
    public ResponseEntity<VideoJobResponse> create(@Valid @RequestBody CreateVideoJobRequest request) {
        var job = videoJobService.createJob(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(VideoJobResponse.from(job));
    }

    @GetMapping("/{id}")
    public VideoJobResponse get(@PathVariable Long id) {
        return VideoJobResponse.from(videoJobService.getJob(id));
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
