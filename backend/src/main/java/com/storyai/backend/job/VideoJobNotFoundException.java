package com.storyai.backend.job;

public class VideoJobNotFoundException extends RuntimeException {

    public VideoJobNotFoundException(Long jobId) {
        super("VideoJob not found: " + jobId);
    }
}
