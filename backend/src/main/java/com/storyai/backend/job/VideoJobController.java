package com.storyai.backend.job;

import com.storyai.backend.job.dto.CreateVideoJobRequest;
import com.storyai.backend.job.dto.VideoJobResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/video-jobs")
@RequiredArgsConstructor
public class VideoJobController {

    private final VideoJobService videoJobService;

    @PostMapping
    public ResponseEntity<VideoJobResponse> create(@Valid @RequestBody CreateVideoJobRequest request) {
        var job = videoJobService.createJob(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(VideoJobResponse.from(job));
    }

    @GetMapping("/{id}")
    public VideoJobResponse get(@PathVariable Long id) {
        return VideoJobResponse.from(videoJobService.getJob(id));
    }
}
