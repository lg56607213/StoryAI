package com.storyai.backend.ai.story;

import com.fasterxml.jackson.databind.JsonNode;
import com.storyai.backend.ai.GeminiClient;
import com.storyai.backend.domain.videojob.AgeGroup;
import com.storyai.backend.domain.videojob.StoryTheme;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class GeminiStoryGenerator implements StoryGenerator {

    private final GeminiClient gemini;

    @Override
    public boolean isAvailable() {
        return gemini.isConfigured();
    }

    @Override
    public StoryOutline outline(StoryTheme theme, String characters, AgeGroup ageGroup, String storyDirection) {
        AgeGroup ag = ageGroup != null ? ageGroup : AgeGroup.AGE_5_6;
        String prompt = StoryPrompts.outline(theme, characters, ag, storyDirection);

        JsonNode json = generateJsonRetry(prompt);
        return new StoryOutline(
                json.path("title").asText("%s 이야기".formatted(theme.getLabel())),
                json.path("synopsis").asText(""));
    }

    @Override
    public List<BookPageDraft> pages(StoryTheme theme, String characters, AgeGroup ageGroup,
                                     StoryOutline outline, int pageCount) {
        AgeGroup ag = ageGroup != null ? ageGroup : AgeGroup.AGE_5_6;
        String prompt = StoryPrompts.pages(theme, characters, ag, outline, pageCount);

        JsonNode json = generateJsonRetry(prompt);
        List<BookPageDraft> pages = new ArrayList<>();
        for (JsonNode p : json.path("pages")) {
            String outfit = "everyday".equals(p.path("outfit").asText("costume")) ? "everyday" : "costume";
            pages.add(new BookPageDraft(p.path("text").asText(""), p.path("scene").asText(""), outfit));
        }
        // 개수가 모자라면 마지막으로 채우고, 넘치면 잘라 정확히 pageCount로 맞춘다.
        while (pages.size() < pageCount) {
            pages.add(pages.isEmpty() ? new BookPageDraft("", "", "costume") : pages.get(pages.size() - 1));
        }
        return pages.subList(0, pageCount);
    }

    /** 일시적 실패(네트워크·5xx 등)로 스토리가 더미로 폴백되는 것을 줄이기 위해 최대 3회 재시도. */
    private JsonNode generateJsonRetry(String prompt) {
        RuntimeException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return gemini.generateJson(prompt);
            } catch (RuntimeException e) {
                last = e;
            }
        }
        throw last;
    }

    private String safe(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }
}
