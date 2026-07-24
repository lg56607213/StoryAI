package com.storyai.backend.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 그림 스타일별 미리보기 샘플 이미지 목록. 프론트 "스타일 선택" 화면에서 예시 그림으로 보여준다.
 * 처음 조회 시 없는 샘플은 백그라운드로 생성되고, 준비되면 이후 즉시 제공된다.
 */
@RestController
@RequestMapping("/api/options/style-samples")
@RequiredArgsConstructor
public class StyleSampleController {

    private final StyleSampleService styleSampleService;

    @GetMapping
    public List<StyleSampleService.StyleSample> list() {
        return styleSampleService.list();
    }
}
