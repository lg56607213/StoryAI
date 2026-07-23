package com.storyai.backend.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class VideoJobExceptionHandler {

    /** 401/403 등 의도적으로 던진 상태 예외는 그대로 통과시킨다(아래 포괄 핸들러에 잡히지 않도록). */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatus(ResponseStatusException e) {
        String reason = e.getReason() != null ? e.getReason() : e.getMessage();
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", reason));
    }

    /** 잘못된 요청 본문(JSON 문법 오류, 알 수 없는 enum 값 등)은 400으로 돌려준다. */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadable(
            org.springframework.http.converter.HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "요청 형식이 올바르지 않습니다."));
    }

    /** 업로드 용량 초과는 413으로 명확히 안내한다. */
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleTooLarge(
            org.springframework.web.multipart.MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("error", "사진 용량이 너무 큽니다. 더 작은 사진으로 다시 시도해 주세요."));
    }

    /**
     * 처리되지 않은 예외 — 원인을 알 수 있게 로그를 남기고 메시지를 함께 반환한다.
     * (기본 설정에서는 메시지가 숨겨져 화면에 "Internal Server Error"만 떠서 원인 파악이 불가능했다)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception e) {
        log.error("처리되지 않은 오류", e);
        String detail = e.getClass().getSimpleName()
                + (e.getMessage() != null ? ": " + e.getMessage() : "");
        if (detail.length() > 300) {
            detail = detail.substring(0, 300);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "처리 중 오류가 발생했어요. (" + detail + ")"));
    }

    @ExceptionHandler(VideoJobNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(VideoJobNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }

    /**
     * 외부 연동 미설정·일시적 사용 불가(예: ELEVENLABS_API_KEY 미설정, ffmpeg 없음).
     * 500 대신 503으로 내려 프론트가 안내 문구를 그대로 보여줄 수 있게 한다.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleUnavailable(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", message));
    }
}
