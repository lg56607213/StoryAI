package com.storyai.backend.pricing;

import com.storyai.backend.domain.videojob.OutputType;
import com.storyai.backend.domain.videojob.VideoJob;

/**
 * 가격 정책 (KRW, VAT 포함).
 * - PDF 다운로드: 24p 9,900원 / 36p 13,200원
 * - 하드커버(인쇄본): 24p 42,900원 / 36p 46,200원 (PDF 파일 포함 제공)
 * - 영상 가격은 아직 미정(null).
 * 모든 표시 가격은 부가세(VAT) 포함가다.
 */
public final class Pricing {

    public static final int BOOK_PDF_24P = 9_900;
    public static final int BOOK_PDF_36P = 13_200;
    public static final int BOOK_HARDCOVER_24P = 42_900;
    public static final int BOOK_HARDCOVER_36P = 46_200;

    private Pricing() {
    }

    /** 프로젝트 선택에 따른 가격(원, VAT 포함). 산정 불가하면 null. */
    public static Integer priceKrw(VideoJob job) {
        if (job.getOutputType() != OutputType.BOOK) {
            return null; // 영상 가격 정책 미정
        }
        return job.isPhysicalBookRequested()
                ? hardcoverPriceKrw(job.getBookPages())
                : pdfPriceKrw(job.getBookPages());
    }

    /** PDF 정가(원, VAT 포함). */
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

    /** 하드커버 정가(원, VAT 포함, PDF 포함 제공). */
    public static Integer hardcoverPriceKrw(Integer pages) {
        if (pages == null) {
            return null;
        }
        return switch (pages) {
            case 24 -> BOOK_HARDCOVER_24P;
            case 36 -> BOOK_HARDCOVER_36P;
            default -> null;
        };
    }
}
