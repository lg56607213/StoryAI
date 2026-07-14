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
        // 삽화 생성이 실패해도 빈 페이지가 나가지 않도록: 직전 성공 삽화 → 없으면 아이 캐릭터 시트를 재사용.
        String lastGoodUrl = null;
        String sheetFallbackUrl = firstSheetUrl(characters);
        int generated = 0, reused = 0, kept = 0;
        for (int idx = 0; idx < pages.size(); idx++) {
            BookPage page = pages.get(idx);
            if (idx >= upTo) {
                continue; // 미리보기: 뒷 페이지는 아직 생성하지 않음
            }
            // 이미 실제 삽화가 있으면(미리보기에서 생성됨) 재사용해 비용 절약.
            if (localStorage.loadByUrl(page.getImageUrl()) != null) {
                lastGoodUrl = page.getImageUrl();
                kept++;
                continue;
            }
            List<byte[]> sheets = sheetsForPage(characters, page.getOutfit());
            String url = null;
            if (canGenerate && generated < illustrateLimit && !sheets.isEmpty()) {
                try {
                    byte[] img = imageGenerator.illustrate(page.getSceneDescription(), sheets, style);
                    url = localStorage.storeGenerated(job.getId(), "page-" + page.getPageNumber() + ".png", img);
                    generated++;
                    lastGoodUrl = url;
                } catch (Exception e) {
                    log.warn("페이지 {} 삽화 실패(재시도 후에도): {}", page.getPageNumber(), e.getMessage());
                }
            }
            if (url == null) {
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
        log.info("삽화 단계 완료: phase={}, 생성 {}, 재사용(기존) {}, 대체 {} / 대상 {}페이지",
                job.getBookPhase(), generated, kept, reused, upTo);
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
