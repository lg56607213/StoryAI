package com.storyai.backend.storage;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cloudflare R2(S3 호환) 저장소. storyai.storage.provider=r2 일 때 활성화된다.
 * 파일만 R2에 두고, DB·서버·API 키는 Railway 그대로 사용한다.
 *
 * 대용량(mp4/pdf) 전송은 백엔드가 대신 하지 않도록 presigned URL로 리다이렉트한다(R2 egress 무료).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "storyai.storage.provider", havingValue = "r2")
public class R2Storage implements StorageService {

    private final String bucket;
    private final S3Client s3;
    private final S3Presigner presigner;
    private final Duration presignTtl;

    public R2Storage(
            @Value("${storyai.storage.r2.account-id:${R2_ACCOUNT_ID:}}") String accountId,
            @Value("${storyai.storage.r2.access-key:${R2_ACCESS_KEY_ID:}}") String accessKey,
            @Value("${storyai.storage.r2.secret-key:${R2_SECRET_ACCESS_KEY:}}") String secretKey,
            @Value("${storyai.storage.r2.bucket:${R2_BUCKET:}}") String bucket,
            @Value("${storyai.storage.r2.presign-minutes:60}") int presignMinutes) {
        this.bucket = bucket;
        this.presignTtl = Duration.ofMinutes(Math.max(5, presignMinutes));
        String endpoint = "https://" + accountId + ".r2.cloudflarestorage.com";
        var creds = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        this.s3 = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of("auto")) // R2는 리전 개념이 없어 "auto"로 서명한다.
                .credentialsProvider(creds)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
        this.presigner = S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of("auto"))
                .credentialsProvider(creds)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    @PostConstruct
    void logConfig() {
        log.info("R2Storage 활성화: bucket={}, writable={}", bucket, writable());
    }

    // --- 저장 ---

    @Override
    public String storeUpload(byte[] bytes, String ext) {
        String safeExt = (ext == null || ext.isBlank()) ? "bin" : ext.replaceAll("[^a-zA-Z0-9]", "");
        String key = "uploads/" + UUID.randomUUID() + "." + safeExt;
        put(key, bytes);
        return URL_PREFIX + key;
    }

    @Override
    public String storeGenerated(Long jobId, String name, byte[] bytes) {
        String key = "gen/" + jobId + "/" + name;
        put(key, bytes);
        return URL_PREFIX + key;
    }

    @Override
    public String storeShared(String name, byte[] bytes) {
        put("shared/" + name, bytes);
        return URL_PREFIX + "shared/" + name;
    }

    @Override
    public void storeBookPdf(Long jobId, byte[] bytes) {
        put(bookPdfKey(jobId), bytes);
    }

    // --- 조회 ---

    @Override
    public byte[] readShared(String name) {
        return get("shared/" + name);
    }

    @Override
    public String sharedUrl(String name) {
        return URL_PREFIX + "shared/" + name;
    }

    @Override
    public byte[] loadByUrl(String url) {
        if (url == null || !url.startsWith(URL_PREFIX)) {
            return null;
        }
        return get(url.substring(URL_PREFIX.length()));
    }

    @Override
    public byte[] readKey(String key) {
        return get(key);
    }

    @Override
    public byte[] readBookPdf(Long jobId) {
        return get(bookPdfKey(jobId));
    }

    @Override
    public boolean bookPdfExists(Long jobId) {
        return exists(bookPdfKey(jobId));
    }

    @Override
    public String redirectUrlForKey(String key) {
        try {
            GetObjectRequest getReq = GetObjectRequest.builder().bucket(bucket).key(key).build();
            GetObjectPresignRequest presignReq = GetObjectPresignRequest.builder()
                    .signatureDuration(presignTtl)
                    .getObjectRequest(getReq)
                    .build();
            return presigner.presignGetObject(presignReq).url().toString();
        } catch (Exception e) {
            log.warn("R2 presign 실패(key={}): {}", key, e.getMessage());
            return null; // 실패 시 FileController가 바이트 스트리밍으로 폴백
        }
    }

    // --- 정리·진단 ---

    @Override
    public long dirSizeBytes(String sub) {
        long total = 0;
        for (S3Object o : listAll(prefix(sub))) {
            total += o.size() != null ? o.size() : 0;
        }
        return total;
    }

    @Override
    public long deleteGenerated(Long jobId) {
        return deletePrefix("gen/" + jobId + "/");
    }

    @Override
    public long deleteAllUploads() {
        return deletePrefix("uploads/");
    }

    @Override
    public void deleteByUrl(String url) {
        if (url == null || !url.startsWith(URL_PREFIX)) {
            return;
        }
        String key = url.substring(URL_PREFIX.length());
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (Exception ignore) {
            // best-effort
        }
    }

    @Override
    public boolean writable() {
        try {
            String probe = "shared/.write-probe";
            put(probe, new byte[]{1});
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(probe).build());
            return true;
        } catch (Exception e) {
            log.warn("R2 쓰기 확인 실패: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public long usableSpaceBytes() {
        return -1; // R2는 실질적으로 무제한
    }

    @Override
    public String rootPath() {
        return "r2://" + bucket;
    }

    // --- 내부 ---

    private String bookPdfKey(Long jobId) {
        return "book-" + jobId + ".pdf";
    }

    /** dirSizeBytes("gen") 처럼 서브명만 오면 접두어로 맞춘다. */
    private String prefix(String sub) {
        if (sub == null || sub.isBlank()) {
            return "";
        }
        return sub.endsWith("/") ? sub : sub + "/";
    }

    private void put(String key, byte[] bytes) {
        try {
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucket).key(key).contentType(contentType(key)).build(),
                    RequestBody.fromBytes(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("R2 저장 실패(key=" + key + "): " + e.getMessage(), e);
        }
    }

    /** 없으면 null(로컬 저장소와 동일한 관용적 동작). */
    private byte[] get(String key) {
        try {
            ResponseBytes<GetObjectResponse> res = s3.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(key).build());
            return res.asByteArray();
        } catch (NoSuchKeyException e) {
            return null;
        } catch (Exception e) {
            log.warn("R2 읽기 실패(key={}): {}", key, e.getMessage());
            return null;
        }
    }

    private boolean exists(String key) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private List<S3Object> listAll(String prefix) {
        List<S3Object> out = new ArrayList<>();
        String token = null;
        try {
            do {
                ListObjectsV2Request.Builder req = ListObjectsV2Request.builder()
                        .bucket(bucket).prefix(prefix).maxKeys(1000);
                if (token != null) {
                    req.continuationToken(token);
                }
                ListObjectsV2Response res = s3.listObjectsV2(req.build());
                out.addAll(res.contents());
                token = Boolean.TRUE.equals(res.isTruncated()) ? res.nextContinuationToken() : null;
            } while (token != null);
        } catch (Exception e) {
            log.warn("R2 목록 조회 실패(prefix={}): {}", prefix, e.getMessage());
        }
        return out;
    }

    private long deletePrefix(String prefix) {
        List<S3Object> objs = listAll(prefix);
        if (objs.isEmpty()) {
            return 0;
        }
        long freed = objs.stream().mapToLong(o -> o.size() != null ? o.size() : 0).sum();
        // 1000개씩 배치 삭제.
        List<ObjectIdentifier> ids = new ArrayList<>();
        for (S3Object o : objs) {
            ids.add(ObjectIdentifier.builder().key(o.key()).build());
            if (ids.size() == 1000) {
                deleteBatch(ids);
                ids.clear();
            }
        }
        if (!ids.isEmpty()) {
            deleteBatch(ids);
        }
        return freed;
    }

    private void deleteBatch(List<ObjectIdentifier> ids) {
        try {
            s3.deleteObjects(DeleteObjectsRequest.builder()
                    .bucket(bucket)
                    .delete(Delete.builder().objects(ids).build())
                    .build());
        } catch (Exception e) {
            log.warn("R2 배치 삭제 실패: {}", e.getMessage());
        }
    }

    private String contentType(String key) {
        String k = key.toLowerCase();
        if (k.endsWith(".png")) return "image/png";
        if (k.endsWith(".jpg") || k.endsWith(".jpeg")) return "image/jpeg";
        if (k.endsWith(".webp")) return "image/webp";
        if (k.endsWith(".pdf")) return "application/pdf";
        if (k.endsWith(".mp4")) return "video/mp4";
        if (k.endsWith(".mp3")) return "audio/mpeg";
        if (k.endsWith(".wav")) return "audio/wav";
        return "application/octet-stream";
    }
}
