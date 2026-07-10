package com.storyai.backend.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 로컬 파일 저장소. S3 연동 전까지 업로드 사진·생성 이미지·PDF를 로컬 디렉터리에 저장/조회한다.
 * URL 규칙: 저장 파일은 "/api/files/{key}" 로 서빙된다 (key = root 기준 상대경로).
 */
@Component
public class LocalStorage {

    public static final String URL_PREFIX = "/api/files/";

    private final Path root;

    public LocalStorage(@Value("${storyai.storage.local-dir:./storage}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    // --- 업로드 사진 ---
    public String storeUpload(byte[] bytes, String ext) {
        String safeExt = (ext == null || ext.isBlank()) ? "bin" : ext.replaceAll("[^a-zA-Z0-9]", "");
        String key = "uploads/" + UUID.randomUUID() + "." + safeExt;
        writeKey(key, bytes);
        return URL_PREFIX + key;
    }

    // --- 생성 이미지 (캐릭터 시트, 페이지 삽화) ---
    public String storeGenerated(Long jobId, String name, byte[] bytes) {
        String key = "gen/" + jobId + "/" + name;
        writeKey(key, bytes);
        return URL_PREFIX + key;
    }

    /** 우리 저장소("/api/files/...") URL로부터 바이트를 읽는다. 외부 URL/placeholder/미존재는 null. */
    public byte[] loadByUrl(String url) {
        if (url == null || !url.startsWith(URL_PREFIX)) {
            return null; // 외부 http URL, placeholder 등은 로컬 파일이 아님
        }
        String key = url.substring(URL_PREFIX.length());
        Path p = resolveKey(key);
        if (p == null || !Files.exists(p)) {
            return null;
        }
        return read(p);
    }

    public byte[] readKey(String key) {
        Path p = resolveKey(key);
        if (p == null || !Files.exists(p)) {
            return null;
        }
        return read(p);
    }

    /** 경로 탈출 방지: root 밖으로 나가거나 잘못된 문자면 null. */
    private Path resolveKey(String key) {
        try {
            Path p = root.resolve(key).normalize();
            return p.startsWith(root) ? p : null;
        } catch (RuntimeException e) {
            return null; // InvalidPathException 등
        }
    }

    private void writeKey(String key, byte[] bytes) {
        Path p = resolveKey(key);
        if (p == null) {
            throw new IllegalArgumentException("잘못된 저장 경로: " + key);
        }
        write(p, bytes);
    }

    // --- 책 PDF (기존) ---
    public Path bookPdfPath(Long jobId) {
        return root.resolve("book-" + jobId + ".pdf");
    }

    public void write(Path path, byte[] bytes) {
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("파일 저장 실패: " + path, e);
        }
    }

    public byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException("파일 읽기 실패: " + path, e);
        }
    }

    public boolean exists(Path path) {
        return Files.exists(path);
    }
}
