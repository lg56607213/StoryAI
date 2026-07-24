package com.storyai.backend.domain.review;

import com.storyai.backend.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 고객 후기 API.
 * - GET  /api/reviews            : 노출용 후기 목록 + 개수/평균 (공개)
 * - POST /api/reviews            : 후기 작성 (로그인 필수, multipart: rating/content/photo?)
 * - GET  /api/reviews/{id}/photo : 후기 사진 서빙
 */
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private static final int MAX_PHOTO_DIM = 1200;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private final ReviewRepository reviewRepository;
    private final StorageService localStorage;

    @GetMapping
    public Map<String, Object> list() {
        List<Review> reviews = reviewRepository.findTop60ByHiddenFalseOrderByCreatedAtDesc();
        double avg = reviews.stream().mapToInt(Review::getRating).average().orElse(0);
        List<Map<String, Object>> items = reviews.stream().map(this::toDto).toList();
        return Map.of(
                "count", reviews.size(),
                "average", Math.round(avg * 10) / 10.0,
                "items", items);
    }

    @PostMapping
    public Map<String, Object> create(
            @RequestParam int rating,
            @RequestParam String content,
            @RequestParam(required = false) MultipartFile photo,
            Authentication authentication) {

        String[] who = requireUser(authentication); // [name, provider, providerId]
        if (rating < 1 || rating > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "별점은 1~5 사이여야 합니다.");
        }
        String text = content == null ? "" : content.trim();
        if (text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "후기 내용을 입력해 주세요.");
        }
        if (text.length() > 1000) {
            text = text.substring(0, 1000);
        }

        String photoUrl = null;
        if (photo != null && !photo.isEmpty()) {
            byte[] jpeg = downscaleJpeg(photo);
            photoUrl = localStorage.storeUpload(jpeg, "jpg"); // "/api/files/uploads/xxx.jpg"
        }

        Review saved = reviewRepository.save(
                new Review(who[0], who[1], who[2], rating, text, photoUrl));
        return toDto(saved);
    }

    @GetMapping("/{id}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable Long id) {
        Review r = reviewRepository.findById(id).orElse(null);
        if (r == null || !r.hasPhoto() || r.isHidden()) {
            return ResponseEntity.notFound().build();
        }
        byte[] bytes = localStorage.loadByUrl(r.getPhotoUrlInternal());
        if (bytes == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(30)).cachePublic())
                .body(bytes);
    }

    // ---- helpers ----

    private Map<String, Object> toDto(Review r) {
        java.util.HashMap<String, Object> m = new java.util.HashMap<>();
        m.put("id", r.getId());
        m.put("authorName", r.getAuthorName());
        m.put("rating", r.getRating());
        m.put("content", r.getContent());
        m.put("photoUrl", r.hasPhoto() ? "/api/reviews/" + r.getId() + "/photo" : null);
        m.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().format(DATE) : null);
        return m;
    }

    /** 로그인 사용자에서 [표시이름, provider, providerId] 추출. 미로그인 시 401. */
    private String[] requireUser(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "후기는 로그인 후 남길 수 있어요.");
        }
        String provider = token.getAuthorizedClientRegistrationId();
        OAuth2User u = token.getPrincipal();
        Map<String, Object> attrs = u.getAttributes();
        String name = null, providerId = null;
        if ("kakao".equals(provider)) {
            providerId = str(attrs.get("id"));
            if (attrs.get("kakao_account") instanceof Map<?, ?> acc
                    && acc.get("profile") instanceof Map<?, ?> p) {
                name = str(p.get("nickname"));
            }
        } else {
            providerId = str(attrs.get("sub"));
            name = str(attrs.get("name"));
        }
        if (name == null || name.isBlank()) {
            name = "회원";
        }
        return new String[]{name, provider, providerId};
    }

    private String str(Object o) {
        return o != null ? o.toString() : null;
    }

    /** 업로드 사진을 최대 1200px, JPEG로 축소. 디코딩 실패 시 400. */
    private byte[] downscaleJpeg(MultipartFile file) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(file.getBytes()));
            if (src == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지를 읽을 수 없습니다.");
            }
            double s = Math.min(1.0, (double) MAX_PHOTO_DIM / Math.max(src.getWidth(), src.getHeight()));
            int w = (int) Math.round(src.getWidth() * s), h = (int) Math.round(src.getHeight() * s);
            BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = dst.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, w, h, null);
            g.dispose();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(dst, "jpeg", baos);
            return baos.toByteArray();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사진 처리에 실패했습니다.");
        }
    }
}
