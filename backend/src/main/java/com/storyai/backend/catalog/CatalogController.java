package com.storyai.backend.catalog;

import com.storyai.backend.domain.storycharacter.CharacterRole;
import com.storyai.backend.domain.videojob.AgeGroup;
import com.storyai.backend.domain.videojob.BookStyle;
import com.storyai.backend.domain.videojob.OutputType;
import com.storyai.backend.domain.videojob.StoryTheme;
import com.storyai.backend.domain.videojob.VideoStyle;
import com.storyai.backend.pricing.Pricing;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 프론트가 선택 UI를 그릴 수 있도록 사용 가능한 옵션 목록을 제공한다.
 * (하드코딩된 enum 카탈로그 — 값이 늘어나면 여기 자동 반영됨)
 */
@RestController
@RequestMapping("/api/options")
public class CatalogController {

    @GetMapping
    public Map<String, Object> options() {
        return Map.of(
                "outputTypes", List.of(
                        option(OutputType.BOOK.name(), OutputType.BOOK.getLabel()),
                        option(OutputType.VIDEO.name(), OutputType.VIDEO.getLabel())
                ),
                "themes", enumOptions(StoryTheme.values(), StoryTheme::name, StoryTheme::getLabel),
                "bookStyles", enumOptions(BookStyle.values(), BookStyle::name, BookStyle::getLabel),
                "bookPageOptions", List.of(24, 36),
                "videoStyles", enumOptions(VideoStyle.values(), VideoStyle::name, VideoStyle::getLabel),
                "videoDurationOptions", List.of(120, 300),
                "characterRoles", enumOptions(CharacterRole.values(), CharacterRole::name, CharacterRole::getLabel),
                "ageGroups", enumOptions(AgeGroup.values(), AgeGroup::name, AgeGroup::getLabel),
                "pricing", pricing()
        );
    }

    private Map<String, Object> pricing() {
        return Map.of(
                "currency", "KRW",
                "vatIncluded", true,
                "bookPdf", List.of(
                        Map.of("pages", 24, "priceKrw", Pricing.BOOK_PDF_24P),
                        Map.of("pages", 36, "priceKrw", Pricing.BOOK_PDF_36P)
                ),
                "bookHardcover", List.of(
                        Map.of("pages", 24, "priceKrw", Pricing.BOOK_HARDCOVER_24P),
                        Map.of("pages", 36, "priceKrw", Pricing.BOOK_HARDCOVER_36P)
                ),
                "bookVideoAddon", List.of(
                        Map.of("pages", 24, "priceKrw", Pricing.BOOK_VIDEO_24P),
                        Map.of("pages", 36, "priceKrw", Pricing.BOOK_VIDEO_36P)
                ),
                // 구매 3티어(총액). 프론트 확정화면이 이 목록으로 선택지를 그린다.
                "bundles", List.of(
                        bundle("PDF", "PDF", false, false),
                        bundle("PDF_VIDEO", "PDF + 읽어주는 영상", true, false),
                        bundle("PDF_VIDEO_BOOK", "PDF + 영상 + 실물책", true, true)
                ),
                "note", "모든 가격 VAT 포함. 실물책 구매 시 PDF 파일도 함께 제공."
        );
    }

    /** 한 구매 티어의 24/36p 총액을 담은 맵. */
    private Map<String, Object> bundle(String code, String label, boolean video, boolean physical) {
        return Map.of(
                "code", code,
                "label", label,
                "video", video,
                "physical", physical,
                "prices", List.of(
                        Map.of("pages", 24, "priceKrw", Pricing.bundlePriceKrw(24, video, physical)),
                        Map.of("pages", 36, "priceKrw", Pricing.bundlePriceKrw(36, video, physical))
                )
        );
    }

    private <E> List<Map<String, String>> enumOptions(E[] values,
                                                      java.util.function.Function<E, String> code,
                                                      java.util.function.Function<E, String> label) {
        return java.util.Arrays.stream(values)
                .map(e -> option(code.apply(e), label.apply(e)))
                .toList();
    }

    private Map<String, String> option(String code, String label) {
        return Map.of("code", code, "label", label);
    }
}
