package com.storyai.backend.storage;

/**
 * 파일 저장소 추상화. 구현은 로컬 볼륨({@link LocalStorage}) 또는 Cloudflare R2({@link R2Storage}).
 * 환경변수 storyai.storage.provider(local|r2)로 선택한다.
 *
 * 저장 파일은 항상 "/api/files/{key}" 규칙의 내부 URL로 참조하며, 실제 서빙은 FileController가 담당한다.
 * key 예: uploads/uuid.jpg, gen/{jobId}/page-1.png, shared/mascot-*.png, book-{jobId}.pdf
 */
public interface StorageService {

    String URL_PREFIX = "/api/files/";

    // --- 저장 ---
    String storeUpload(byte[] bytes, String ext);
    String storeGenerated(Long jobId, String name, byte[] bytes);
    String storeShared(String name, byte[] bytes);

    // --- 조회 ---
    byte[] readShared(String name);
    String sharedUrl(String name);
    byte[] loadByUrl(String url);
    byte[] readKey(String key);

    // --- 책 PDF (키 기반: book-{jobId}.pdf) ---
    void storeBookPdf(Long jobId, byte[] bytes);
    byte[] readBookPdf(Long jobId);
    boolean bookPdfExists(Long jobId);

    /**
     * 서빙 최적화: 이 key를 클라이언트가 직접 받을 절대 URL(R2 presigned 등). null이면 백엔드가 바이트를 스트리밍한다.
     * R2에서는 대용량(mp4/pdf) 전송을 백엔드가 대신 하지 않도록 presigned URL로 리다이렉트한다.
     */
    default String redirectUrlForKey(String key) {
        return null;
    }

    // --- 정리·진단 ---
    long dirSizeBytes(String sub);
    long deleteGenerated(Long jobId);
    long deleteAllUploads();
    void deleteByUrl(String url);
    boolean writable();
    long usableSpaceBytes();
    String rootPath();
}
