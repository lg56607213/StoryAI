package com.storyai.backend.workflow.handler;

import com.storyai.backend.ai.image.ImageGenerator;
import com.storyai.backend.domain.bookpage.BookPage;
import com.storyai.backend.domain.bookpage.BookPageRepository;
import com.storyai.backend.domain.storycharacter.StoryCharacter;
import com.storyai.backend.domain.storycharacter.StoryCharacterRepository;
import com.storyai.backend.domain.videojob.BookPhase;
import com.storyai.backend.domain.videojob.VideoJob;
import com.storyai.backend.domain.videojob.WorkflowStep;
import com.storyai.backend.storage.LocalStorage;
import com.storyai.backend.workflow.WorkflowStepHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Slf4j
@Component
@RequiredArgsConstructor
public class PageIllustrationStepHandler implements WorkflowStepHandler {

    private final BookPageRepository bookPageRepository;
    private final StoryCharacterRepository storyCharacterRepository;
    private final ImageGenerator imageGenerator;
    private final LocalStorage localStorage;

    /** 개발/테스트 비용 제어: 실제 삽화를 생성할 최대 페이지 수 (초과분은 placeholder). 기본 무제한. */
    @Value("${storyai.book.illustrate-limit:9999}")
    private int illustrateLimit;

    /** 미리보기 단계에서 실제로 그릴 앞쪽 페이지 수(표지 배경 포함). */
    @Value("${storyai.book.preview-pages:4}")
    private int previewPages;

    /** 삽화 병렬 생성 동시 실행 수(Gemini 부하/429 제어). */
    @Value("${storyai.book.illustrate-concurrency:4}")
    private int illustrateConcurrency;

    @Override
    public WorkflowStep getStep() {
        return WorkflowStep.PAGE_ILLUSTRATION;
    }

    @Override
    public void execute(VideoJob job) {
        List<BookPage> pages = bookPageRepository.findByVideoJobIdOrderByPageNumberAsc(job.getId());
        List<StoryCharacter> characters = storyCharacterRepository.findByVideoJobIdOrderByIdAsc(job.getId());

        boolean canGenerate = imageGenerator.isAvailable() && hasAnySheet(characters);
        if (!canGenerate) {
            log.info("삽화 실제 생성 불가(키/시트 없음) → placeholder 사용");
        }

        String style = job.getBookStyle() != null ? job.getBookStyle().getGuide() : null;
        // 미리보기 단계면 앞쪽 previewPages 페이지만 그린다(비용 최소화). 전체 단계면 전부.
        boolean preview = job.getBookPhase() == BookPhase.PREVIEW;
        int upTo = preview ? Math.min(previewPages, pages.size()) : pages.size();
        String sheetFallbackUrl = firstSheetUrl(characters);

        // 1) 생성 대상 선별: phase 범위 내 & 아직 실제 삽화 없는 페이지(비용 한도까지).
        List<Integer> targets = new ArrayList<>();
        if (canGenerate) {
            for (int idx = 0; idx < upTo; idx++) {
                if (localStorage.loadByUrl(pages.get(idx).getImageUrl()) == null) {
                    targets.add(idx);
                }
            }
            if (targets.size() > illustrateLimit) {
                targets = targets.subList(0, illustrateLimit);
            }
        }

        // 2) 삽화를 병렬로 생성한다(각 페이지는 동일 캐릭터 시트를 참조 → 순서와 무관하게 일관성 유지).
        //    JPA 저장은 여기서 하지 않고(스레드 안전), 결과 URL만 모은다.
        Map<Integer, String> generatedUrls = new ConcurrentHashMap<>();
        int poolSize = Math.max(1, Math.min(illustrateConcurrency, Math.max(1, targets.size())));
        if (!targets.isEmpty()) {
            ExecutorService pool = Executors.newFixedThreadPool(poolSize);
            try {
                List<Future<?>> futures = new ArrayList<>();
                for (int idx : targets) {
                    final int i = idx;
                    final BookPage page = pages.get(i);
                    futures.add(pool.submit(() -> {
                        List<byte[]> sheets = sheetsForPage(characters, page.getOutfit());
                        if (sheets.isEmpty()) {
                            return;
                        }
                        try {
                            byte[] img = imageGenerator.illustrate(page.getSceneDescription(), sheets, style);
                            String url = localStorage.storeGenerated(
                                    job.getId(), "page-" + page.getPageNumber() + ".png", img);
                            generatedUrls.put(i, url);
                        } catch (Exception e) {
                            log.warn("페이지 {} 삽화 실패(재시도 후에도): {}", page.getPageNumber(), e.getMessage());
                        }
                    }));
                }
                for (Future<?> f : futures) {
                    try {
                        f.get();
                    } catch (Exception e) {
                        log.warn("삽화 작업 대기 오류: {}", e.getMessage());
                    }
                }
            } finally {
                pool.shutdown();
            }
        }

        // 3) 페이지 순서대로 결과를 반영 + 빈 페이지 방지(직전 성공 삽화 → 없으면 아이 시트 재사용). 저장은 메인 스레드.
        String lastGoodUrl = null;
        int generated = 0, reused = 0, kept = 0;
        for (int idx = 0; idx < upTo; idx++) {
            BookPage page = pages.get(idx);
            // 이미 실제 삽화가 있으면(미리보기에서 생성됨) 재사용해 비용 절약.
            if (localStorage.loadByUrl(page.getImageUrl()) != null) {
                lastGoodUrl = page.getImageUrl();
                kept++;
                continue;
            }
            String url = generatedUrls.get(idx);
            if (url != null) {
                generated++;
                lastGoodUrl = url;
            } else {
                // 빈 페이지 절대 금지: 실제 이미지(직전 페이지 → 아이 시트)를 재사용.
                url = lastGoodUrl != null ? lastGoodUrl : sheetFallbackUrl;
                if (url != null) {
                    reused++;
                    log.info("페이지 {} → 대체 이미지 재사용(빈 페이지 방지)", page.getPageNumber());
                } else {
                    url = placeholder(job, page); // 최후의 수단(키/시트 전무한 개발 상황)
                }
            }
            page.setImageUrl(url);
            bookPageRepository.save(page);
        }
        log.info("삽화 단계 완료(병렬 동시{}): phase={}, 생성 {}, 재사용(기존) {}, 대체 {} / 대상 {}페이지",
                poolSize, job.getBookPhase(), generated, kept, reused, upTo);
    }

    /** 페이지 의상(everyday/costume)에 맞는 캐릭터 시트를 인물별로 고른다. 없으면 다른 시트로 폴백. */
    private List<byte[]> sheetsForPage(List<StoryCharacter> characters, String outfit) {
        boolean everyday = "everyday".equals(outfit);
        List<byte[]> sheets = new ArrayList<>();
        for (StoryCharacter c : characters) {
            String primary = everyday ? c.getEverydaySheetUrl() : c.getCharacterSheetUrl();
            String fallback = everyday ? c.getCharacterSheetUrl() : c.getEverydaySheetUrl();
            byte[] bytes = localStorage.loadByUrl(primary);
            if (bytes == null) {
                bytes = localStorage.loadByUrl(fallback);
            }
            if (bytes != null) {
                sheets.add(bytes);
            }
        }
        return sheets;
    }

    private boolean hasAnySheet(List<StoryCharacter> characters) {
        return characters.stream().anyMatch(c ->
                localStorage.loadByUrl(c.getCharacterSheetUrl()) != null
                        || localStorage.loadByUrl(c.getEverydaySheetUrl()) != null);
    }

    /** 삽화 실패 시 최후의 실제 이미지로 쓸 캐릭터 시트 URL(주인공 우선). 없으면 null. */
    private String firstSheetUrl(List<StoryCharacter> characters) {
        for (StoryCharacter c : characters) {
            if (localStorage.loadByUrl(c.getEverydaySheetUrl()) != null) {
                return c.getEverydaySheetUrl();
            }
            if (localStorage.loadByUrl(c.getCharacterSheetUrl()) != null) {
                return c.getCharacterSheetUrl();
            }
        }
        return null;
    }

    private String placeholder(VideoJob job, BookPage page) {
        return "https://placeholder.storyai/%d/page-%d.png".formatted(job.getId(), page.getPageNumber());
    }
}
