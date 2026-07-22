package com.storyai.backend.domain.bookpage;

import com.storyai.backend.domain.videojob.VideoJob;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 동화책의 한 페이지. PAGE_PLANNING에서 페이지 수만큼 생성(문구)되고,
 * PAGE_ILLUSTRATION에서 삽화 URL이 채워진 뒤, PDF_GENERATION에서 PDF로 조판된다.
 */
@Entity
@Table(name = "book_page")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookPage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_job_id", nullable = false)
    private VideoJob videoJob;

    @Column(nullable = false)
    private Integer pageNumber;

    /** 페이지에 들어갈 이야기 문구 */
    @Setter
    @Column(columnDefinition = "TEXT")
    private String text;

    /** 삽화 생성을 위한 장면 설명(영문 프롬프트용). */
    @Setter
    @Column(columnDefinition = "TEXT")
    private String sceneDescription;

    /** 이 페이지의 의상: "everyday"(실제 옷) 또는 "costume"(주제 의상). 어떤 캐릭터 시트를 참조할지 결정. */
    @Setter
    private String outfit;

    /** 삽화 이미지 URL (PAGE_ILLUSTRATION에서 채움) */
    @Setter
    @Column(length = 1000)
    private String imageUrl;

    /**
     * 멀티보이스 낭독용 세그먼트(서술/대사 + 화자 + 목소리 힌트) JSON 배열.
     * 영상 낭독 단계에서 목소리를 나눠 읽는 데 사용. 없으면(구버전/폴백) text 전체를 내레이터가 읽는다.
     */
    @Setter
    @Column(columnDefinition = "TEXT")
    private String narrationJson;
}
