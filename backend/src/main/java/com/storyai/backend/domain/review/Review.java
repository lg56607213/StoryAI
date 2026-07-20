package com.storyai.backend.domain.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 고객 후기(별점 + 글 + 선택 사진). 로그인한 사용자만 작성한다.
 * 사진은 LocalStorage(볼륨)에 저장하고 내부 URL만 보관, /api/reviews/{id}/photo 로 서빙한다.
 * hidden=true 면 목록에서 숨김(운영 중 부적절 후기 차단용 레버).
 */
@Entity
@Table(name = "review")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 작성자 표시 이름(계정 이름). */
    @Column(nullable = false)
    private String authorName;

    /** 누가 썼는지 식별(중복/신고 대응). provider = google|kakao */
    private String provider;
    private String providerId;

    @Column(nullable = false)
    private int rating; // 1~5

    @Column(nullable = false, length = 1000)
    private String content;

    /** LocalStorage 내부 URL("/api/files/..."). null이면 사진 없음. 외부로 직접 노출하지 않음. */
    private String photoUrlInternal;

    @Setter
    @Column(nullable = false)
    private boolean hidden = false;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public Review(String authorName, String provider, String providerId, int rating, String content,
                  String photoUrlInternal) {
        this.authorName = authorName;
        this.provider = provider;
        this.providerId = providerId;
        this.rating = rating;
        this.content = content;
        this.photoUrlInternal = photoUrlInternal;
    }

    public boolean hasPhoto() {
        return photoUrlInternal != null && !photoUrlInternal.isBlank();
    }
}
