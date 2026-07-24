package com.storyai.backend.catalog;

import com.storyai.backend.ai.image.ImageGenerator;
import com.storyai.backend.domain.videojob.BookStyle;
import com.storyai.backend.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 그림 스타일별 "미리보기 샘플 이미지"를 스타일당 딱 1회 생성해 영구 캐시한다.
 * 모든 스타일에 동일한 장면을 그려서 사용자가 화풍(색감·질감)만 비교할 수 있게 한다.
 * 첫 조회 시 없으면 백그라운드로 생성하고, 완성되면 이후로는 캐시된 이미지를 즉시 제공한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StyleSampleService {

    private final ImageGenerator imageGenerator;
    private final StorageService storage;

    /** 스타일별 생성 진행 중 표시(중복 생성 방지). */
    private final Map<String, Boolean> generating = new ConcurrentHashMap<>();

    public record StyleSample(String code, String label, String url, boolean ready) {
    }

    /** 스타일 목록 + 각 샘플 이미지 URL. 없으면 백그라운드 생성을 트리거하고 url은 그대로 반환(준비되면 표시됨). */
    public List<StyleSample> list() {
        List<StyleSample> out = new ArrayList<>();
        for (BookStyle style : BookStyle.values()) {
            String name = fileName(style);
            boolean ready = storage.readShared(name) != null;
            if (!ready) {
                triggerGenerate(style);
            }
            out.add(new StyleSample(style.name(), style.getLabel(), storage.sharedUrl(name), ready));
        }
        return out;
    }

    private void triggerGenerate(BookStyle style) {
        if (!imageGenerator.isAvailable()) {
            return;
        }
        // 이미 생성 중이면 건너뜀.
        if (generating.putIfAbsent(style.name(), Boolean.TRUE) != null) {
            return;
        }
        generateAsync(style);
    }

    @Async("mediaTaskExecutor")
    public void generateAsync(BookStyle style) {
        String name = fileName(style);
        try {
            if (storage.readShared(name) != null) {
                return;
            }
            byte[] img = imageGenerator.styleSample(style.getGuide());
            storage.storeShared(name, img);
            log.info("🎨 스타일 샘플 최초 생성: {} — 이후 재사용", style.getLabel());
        } catch (Throwable e) {
            log.warn("스타일 샘플 생성 실패({}): {}", style.getLabel(), e.getMessage());
        } finally {
            generating.remove(style.name());
        }
    }

    private String fileName(BookStyle style) {
        return "style-sample-" + style.name() + ".png";
    }
}
