package com.storyai.backend.pricing;

import com.storyai.backend.domain.videojob.OutputType;
import com.storyai.backend.domain.videojob.VideoJob;

/**
 * 가격 정책 (KRW). talecoco 참고:
 * - PDF 다운로드: 24p 20,000원 / 36p 30,000원
 * - 하드카피(인쇄본) 요청: 60,000원 (인쇄+배송 포함)
 * - 영상 가격은 아직 미정(null).
 * 결제 연동은 후순위이며, 지금은 견적 표시용으로만 사용한다.
 */
public final class Pricing {

    public static final int BOOK_PDF_24P = 20_000;
    public static final int BOOK_PDF_36P = 30_000;
    public static final int BOOK_HARDCOPY = 60_000;

    private Pricing() {
    }

    /** 프로젝트 선택에 따른 가격(원). 산정 불가하면 null. */
    public static Integer priceKrw(VideoJob job) {
        if (job.getOutputType() != OutputType.BOOK) {
            return null; // 영상 가격 정책 미정
        }
        if (job.isPhysicalBookRequested()) {
            return BOOK_HARDCOPY;
        }
        return pdfPriceKrw(job.getBookPages());
    }

    public static Integer pdfPriceKrw(Integer pages) {
        if (pages == null) {
            return null;
        }
        return switch (pages) {
            case 24 -> BOOK_PDF_24P;
            case 36 -> BOOK_PDF_36P;
            default -> null;
        };
    }
}
