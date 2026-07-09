package com.storyai.backend.job;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class VideoJobExceptionHandler {

    @ExceptionHandler(VideoJobNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(VideoJobNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }
}
