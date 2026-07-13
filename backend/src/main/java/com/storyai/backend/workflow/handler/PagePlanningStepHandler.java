package com.storyai.backend.workflow.handler;

import com.storyai.backend.ai.story.BookPageDraft;
import com.storyai.backend.ai.story.StoryGenerator;
import com.storyai.backend.ai.story.StoryOutline;
import com.storyai.backend.domain.bookpage.BookPage;
import com.storyai.backend.domain.bookpage.BookPageRepository;
import com.storyai.backend.domain.videojob.VideoJob;
import com.storyai.backend.domain.videojob.WorkflowStep;
import com.storyai.backend.workflow.WorkflowStepHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.IntStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class PagePlanningStepHandler implements WorkflowStepHandler {

    private static final int DEFAULT_PAGES = 24;

    private final BookPageRepository bookPageRepository;
    private final StoryGenerator storyGenerator;

    @Override
    public WorkflowStep getStep() {
        return WorkflowStep.PAGE_PLANNING;
    }

    @Override
    public void execute(VideoJob job) {
        int pages = job.getBookPages() != null ? job.getBookPages() : DEFAULT_PAGES;
        List<BookPageDraft> drafts = generateDrafts(job, pages);

        for (int i = 0; i < pages; i++) {
            BookPageDraft d = drafts.get(i);
            BookPage page = BookPage.builder()
                    .videoJob(job)
                    .pageNumber(i + 1)
                    .text(d.text())
                    .sceneDescription(d.scene())
                    .outfit(d.outfit())
                    .build();
            bookPageRepository.save(page);
        }
    }

    private List<BookPageDraft> generateDrafts(VideoJob job, int pages) {
        if (storyGenerator.isAvailable()) {
            try {
                StoryOutline outline = new StoryOutline(job.getGeneratedTitle(), job.getSynopsis());
                return storyGenerator.pages(
                        job.getStoryTheme(), job.getProtagonistDescription(), job.getTargetAgeGroup(), outline, pages);
            } catch (Exception e) {
                log.warn("페이지 생성 실패, 더미로 폴백: {}", e.getMessage());
            }
        }
        // 폴백: 줄거리 기반 더미
        return IntStream.rangeClosed(1, pages)
                .mapToObj(i -> new BookPageDraft(
                        "%s (%d/%d)".formatted(job.getSynopsis(), i, pages),
                        "A gentle storybook scene featuring %s.".formatted(job.getProtagonistDescription()),
                        i <= 2 ? "everyday" : "costume"))
                .toList();
    }
}
