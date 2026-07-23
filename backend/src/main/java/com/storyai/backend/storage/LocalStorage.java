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

    // --- 공용 에셋 (잡에 종속되지 않음: 마스코트 "히어로 친구들" 시트 등) ---

    /** 공용 에셋 저장. 한 번 만들면 모든 주문에서 재사용한다. */
    public String storeShared(String name, byte[] bytes) {
        writeKey("shared/" + name, bytes);
        return URL_PREFIX + "shared/" + name;
    }

    /** 공용 에셋 읽기. 없으면 null. */
    public byte[] readShared(String name) {
        return readKey("shared/" + name);
    }

    /** 공용 에셋 URL(존재 여부와 무관하게 규칙상의 URL). */
    public String sharedUrl(String name) {
        return URL_PREFIX + "shared/" + name;
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

    // --- 진단용(관리자 화면) ---

    /** 저장 루트 경로. */
    public String rootPath() {
        return root.toString();
    }

    /** 저장 가능한 남은 용량(바이트). 확인 불가하면 -1. */
    public long usableSpaceBytes() {
        try {
            Files.createDirectories(root);
            return root.toFile().getUsableSpace();
        } catch (Exception e) {
            return -1;
        }
    }

    /** 실제로 파일을 쓸 수 있는지(권한·용량 포함) 확인한다. */
    public boolean writable() {
        try {
            Files.createDirectories(root);
            Path probe = root.resolve(".write-probe");
            Files.write(probe, new byte[]{1});
            Files.deleteIfExists(probe);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
