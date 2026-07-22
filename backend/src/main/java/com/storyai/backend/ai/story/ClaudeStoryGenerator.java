package com.storyai.backend.ai.story;

import com.fasterxml.jackson.databind.JsonNode;
import com.storyai.backend.ai.ClaudeClient;
import com.storyai.backend.domain.videojob.AgeGroup;
import com.storyai.backend.domain.videojob.StoryTheme;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 스토리(글) 생성을 Claude로 수행한다(이미지는 Gemini 유지 — 하이브리드).
 * ANTHROPIC_API_KEY가 없으면 Gemini로 폴백하고, Claude 호출이 실패해도 Gemini로 폴백한다.
 * @Primary 로 등록해 워크플로우가 이 생성기를 우선 사용하게 한다.
 */
@Slf4j
@Primary
@Component
@RequiredArgsConstructor
public class ClaudeStoryGenerator implements StoryGenerator {

    private final ClaudeClient claude;
    private final GeminiStoryGenerator gemini; // 키 없거나 실패 시 폴백

    @Override
    public boolean isAvailable() {
        return claude.isConfigured() || gemini.isAvailable();
    }

    @Override
    public StoryOutline outline(StoryTheme theme, String characters, AgeGroup ageGroup, String storyDirection) {
        if (!claude.isConfigured()) {
            return gemini.outline(theme, characters, ageGroup, storyDirection);
        }
        AgeGroup ag = ageGroup != null ? ageGroup : AgeGroup.AGE_5_6;
        try {
            JsonNode json = claude.generateJson(StoryPrompts.SYSTEM,
                    StoryPrompts.outline(theme, characters, ag, storyDirection));
            return new StoryOutline(
                    json.path("title").asText("%s 이야기".formatted(theme.getLabel())),
                    json.path("synopsis").asText(""));
        } catch (RuntimeException e) {
            log.warn("Claude 개요 생성 실패 → Gemini 폴백: {}", e.getMessage());
            return gemini.outline(theme, characters, ageGroup, storyDirection);
        }
    }

    @Override
    public List<BookPageDraft> pages(StoryTheme theme, String characters, AgeGroup ageGroup,
                                     StoryOutline outline, int pageCount) {
        if (!claude.isConfigured()) {
            return gemini.pages(theme, characters, ageGroup, outline, pageCount);
        }
        AgeGroup ag = ageGroup != null ? ageGroup : AgeGroup.AGE_5_6;
        try {
            JsonNode json = claude.generateJson(StoryPrompts.SYSTEM,
                    StoryPrompts.pages(theme, characters, ag, outline, pageCount));
            return PageDraftParser.parse(json, pageCount);
        } catch (RuntimeException e) {
            log.warn("Claude 페이지 생성 실패 → Gemini 폴백: {}", e.getMessage());
            return gemini.pages(theme, characters, ageGroup, outline, pageCount);
        }
    }
}
