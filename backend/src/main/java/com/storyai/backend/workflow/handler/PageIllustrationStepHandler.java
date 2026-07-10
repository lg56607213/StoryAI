package com.storyai.backend.workflow.handler;

import com.storyai.backend.ai.image.ImageGenerator;
import com.storyai.backend.domain.bookpage.BookPage;
import com.storyai.backend.domain.bookpage.BookPageRepository;
import com.storyai.backend.domain.storycharacter.StoryCharacter;
import com.storyai.backend.domain.storycharacter.StoryCharacterRepository;
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

    @Override
    public WorkflowStep getStep() {
        return WorkflowStep.PAGE_ILLUSTRATION;
    }

    @Override
    public void execute(VideoJob job) {
        List<BookPage> pages = bookPageRepository.findByVideoJobIdOrderByPageNumberAsc(job.getId());

        List<byte[]> sheets = loadCharacterSheets(job);
        boolean canGenerate = imageGenerator.isAvailable() && !sheets.isEmpty();
        if (!canGenerate) {
            log.info("삽화 실제 생성 불가(키/시트 없음) → placeholder 사용");
        }

        int generated = 0;
        for (BookPage page : pages) {
            if (canGenerate && generated < illustrateLimit) {
                try {
                    byte[] img = imageGenerator.illustrate(page.getSceneDescription(), sheets);
                    String url = localStorage.storeGenerated(job.getId(), "page-" + page.getPageNumber() + ".png", img);
                    page.setImageUrl(url);
                    generated++;
                } catch (Exception e) {
                    log.warn("페이지 {} 삽화 실패, placeholder: {}", page.getPageNumber(), e.getMessage());
                    page.setImageUrl(placeholder(job, page));
                }
            } else {
                page.setImageUrl(placeholder(job, page));
            }
            bookPageRepository.save(page);
        }
        if (canGenerate && pages.size() > illustrateLimit) {
            log.info("비용 제한(illustrate-limit={}): {}/{} 페이지만 실제 삽화 생성", illustrateLimit, generated, pages.size());
        }
    }

    private List<byte[]> loadCharacterSheets(VideoJob job) {
        List<byte[]> sheets = new ArrayList<>();
        for (StoryCharacter c : storyCharacterRepository.findByVideoJobIdOrderByIdAsc(job.getId())) {
            byte[] bytes = localStorage.loadByUrl(c.getCharacterSheetUrl());
            if (bytes != null) {
                sheets.add(bytes);
            }
        }
        return sheets;
    }

    private String placeholder(VideoJob job, BookPage page) {
        return "https://placeholder.storyai/%d/page-%d.png".formatted(job.getId(), page.getPageNumber());
    }
}
