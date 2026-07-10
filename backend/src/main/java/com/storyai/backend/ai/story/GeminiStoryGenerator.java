package com.storyai.backend.ai.story;

import com.fasterxml.jackson.databind.JsonNode;
import com.storyai.backend.ai.GeminiClient;
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
    public StoryOutline outline(StoryTheme theme, String characters, String ageGroup) {
        String prompt = """
                너는 유아용 그림동화 작가야. 아래 조건으로 따뜻하고 안전한 한국어 동화의 개요를 만들어줘.
                - 주제: %s
                - 주인공(들): %s
                - 대상 연령: %s
                주인공 이름을 이야기에 실제로 등장시켜줘. 폭력적이거나 무서운 내용은 금지.
                반드시 아래 JSON 형식으로만 답해: {"title": "동화 제목", "synopsis": "3~4문장 줄거리"}
                """.formatted(theme.getLabel(), safe(characters), safe(ageGroup));

        JsonNode json = gemini.generateJson(prompt);
        return new StoryOutline(
                json.path("title").asText("%s 이야기".formatted(theme.getLabel())),
                json.path("synopsis").asText(""));
    }

    @Override
    public List<BookPageDraft> pages(StoryTheme theme, String characters, String ageGroup,
                                     StoryOutline outline, int pageCount) {
        String prompt = """
                아래 동화를 유아용 그림책 %d페이지로 나눠줘.
                - 제목: %s
                - 줄거리: %s
                - 주제: %s / 등장인물: %s / 연령: %s
                각 페이지 객체는 다음 두 필드를 가진다:
                  "text": 1~2문장의 쉽고 리듬감 있는 한국어 이야기 문구
                  "scene": 삽화 생성용 장면 설명(영문, 배경/동작/등장인물/분위기 포함, 한 문장)
                처음-중간-끝의 흐름을 갖추고 마지막은 따뜻하게 마무리. 정확히 %d개.
                반드시 JSON만: {"pages": [{"text": "...", "scene": "..."}, ...]}
                """.formatted(pageCount, safe(outline.title()), safe(outline.synopsis()),
                theme.getLabel(), safe(characters), safe(ageGroup), pageCount);

        JsonNode json = gemini.generateJson(prompt);
        List<BookPageDraft> pages = new ArrayList<>();
        for (JsonNode p : json.path("pages")) {
            pages.add(new BookPageDraft(p.path("text").asText(""), p.path("scene").asText("")));
        }
        // 개수가 모자라면 마지막으로 채우고, 넘치면 잘라 정확히 pageCount로 맞춘다.
        while (pages.size() < pageCount) {
            pages.add(pages.isEmpty() ? new BookPageDraft("", "") : pages.get(pages.size() - 1));
        }
        return pages.subList(0, pageCount);
    }

    private String safe(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }
}
