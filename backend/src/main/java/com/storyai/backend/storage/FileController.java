package com.storyai.backend.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 사진 업로드 및 저장 파일 서빙.
 * - POST /api/uploads : 고객이 아이 사진을 업로드 → 저장 후 URL 반환 (여러 장 가능)
 * - GET  /api/files/** : 저장된 사진/생성 이미지 서빙
 */
@RestController
@RequiredArgsConstructor
public class FileController {

    private final LocalStorage localStorage;

    @PostMapping("/api/uploads")
    public Map<String, List<String>> upload(@RequestParam("files") MultipartFile[] files) {
        List<String> urls = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue;
            }
            String ext = extensionOf(file.getOriginalFilename(), file.getContentType());
            try {
                urls.add(localStorage.storeUpload(file.getBytes(), ext));
            } catch (Exception e) {
                // 원인을 문구에 포함한다(감춰지면 "업로드 실패"만 보여 원인 파악이 불가능했다).
                Throwable root = e;
                while (root.getCause() != null) {
                    root = root.getCause();
                }
                throw new IllegalStateException("업로드 실패: " + file.getOriginalFilename()
                        + " (" + root.getClass().getSimpleName() + ": " + root.getMessage() + ")", e);
            }
        }
        if (urls.isEmpty()) {
            throw new IllegalArgumentException("업로드된 파일이 없습니다.");
        }
        return Map.of("urls", urls);
    }

    @GetMapping("/api/files/**")
    public ResponseEntity<byte[]> serve(HttpServletRequest request) {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String key = path.substring("/api/files/".length());
        byte[] bytes = localStorage.readKey(key);
        if (bytes == null) {
            return ResponseEntity.notFound().build();
        }
        MediaType type = key.endsWith(".png") ? MediaType.IMAGE_PNG
                : (key.endsWith(".jpg") || key.endsWith(".jpeg")) ? MediaType.IMAGE_JPEG
                : key.endsWith(".mp4") ? MediaType.valueOf("video/mp4")
                : key.endsWith(".mp3") ? MediaType.valueOf("audio/mpeg")
                : key.endsWith(".wav") ? MediaType.valueOf("audio/wav")
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(bytes);
    }

    private String extensionOf(String filename, String contentType) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf('.') + 1);
        }
        if (contentType != null && contentType.contains("/")) {
            return contentType.substring(contentType.indexOf('/') + 1);
        }
        return "jpg";
    }
}
