package com.storyai.backend.pricing;

import com.storyai.backend.domain.videojob.OutputType;
import com.storyai.backend.domain.videojob.VideoJob;

/**
 * 가격 정책 (KRW, VAT 포함). 구매는 3티어로 구성한다:
 *  1) PDF                : 24p 9,900 / 36p 13,200
 *  2) PDF + 영상          : PDF + 영상추가금(24p +5,500 / 36p +7,700)
 *  3) PDF + 영상 + 실물책 : 하드커버(PDF 포함) + 영상추가금
 * 하드커버 정가(24p 42,900 / 36p 46,200)에는 PDF가 포함된다.
 * 모든 표시 가격은 부가세(VAT) 포함가다.
 */
public final class Pricing {

    public static final int BOOK_PDF_24P = 9_900;
    public static final int BOOK_PDF_36P = 13_200;
    public static final int BOOK_HARDCOVER_24P = 42_900;
    public static final int BOOK_HARDCOVER_36P = 46_200;
    /** 읽어주는 영상 추가금(PDF/하드커버에 얹는다). */
    public static final int BOOK_VIDEO_24P = 5_500;
    public static final int BOOK_VIDEO_36P = 7_700;

    private Pricing() {
    }

    /** 프로젝트 선택(구매 티어)에 따른 총액(원, VAT 포함). 산정 불가하면 null. */
    public static Integer priceKrw(VideoJob job) {
        if (job.getOutputType() != OutputType.BOOK) {
            return null; // 영상 단독 상품 정책 미정
        }
        return bundlePriceKrw(job.getBookPages(), includesVideo(job), includesPhysical(job));
    }

    /** 티어 총액 = (실물이면 하드커버, 아니면 PDF) + (영상 포함 시 영상추가금). */
    public static Integer bundlePriceKrw(Integer pages, boolean video, boolean physical) {
        Integer base = physical ? hardcoverPriceKrw(pages) : pdfPriceKrw(pages);
        if (base == null) {
            return null;
        }
        if (video) {
            Integer v = videoPriceKrw(pages);
            if (v != null) {
                base += v;
            }
        }
        return base;
    }

    /** 이 주문이 영상을 포함하는가(구매유형 코드 또는 저장된 플래그로 판정). */
    public static boolean includesVideo(VideoJob job) {
        String pt = job.getPurchaseType();
        return job.isVideoIncluded() || (pt != null && pt.contains("VIDEO"));
    }

    /** 이 주문이 실물책(하드커버)을 포함하는가. (구버전 코드 "BOOK" 호환) */
    public static boolean includesPhysical(VideoJob job) {
        String pt = job.getPurchaseType();
        return job.isPhysicalBookRequested()
                || "PDF_VIDEO_BOOK".equals(pt) || "BOOK".equals(pt);
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

    /** 영상 추가금(원, VAT 포함). */
    public static Integer videoPriceKrw(Integer pages) {
        if (pages == null) {
            return null;
        }
        return switch (pages) {
            case 24 -> BOOK_VIDEO_24P;
            case 36 -> BOOK_VIDEO_36P;
            default -> null;
        };
    }
}
