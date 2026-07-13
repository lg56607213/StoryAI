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
        String direction = (storyDirection == null || storyDirection.isBlank())
                ? "특별한 요청 없음 (주제에 맞게 자유롭게)" : storyDirection;
        String prompt = """
                너는 그림동화 작가야. 아래 조건으로 따뜻하고 안전한 한국어 동화의 개요를 만들어줘.
                - 주제: %s
                - 주인공(들): %s
                - 대상 연령: %s (%s)
                - 스토리 방향(고객 요청): %s
                주제와 스토리 방향을 반영하되, 주인공 이름을 이야기에 실제로 등장시켜줘. 폭력적이거나 무서운 내용은 금지.
                매우 중요(등장인물 고정): 사람 등장인물은 오직 위 "주인공(들)" 목록에 있는 인물만 사용해.
                목록에 없는 다른 아이·친구·형제·동생·부모 등 사람을 새로 만들어 등장시키지 마
                (주제에 맞는 동물이나 상상 속 캐릭터 조력자는 괜찮음).
                반드시 아래 JSON 형식으로만 답해: {"title": "동화 제목", "synopsis": "3~4문장 줄거리"}
                """.formatted(theme.getLabel(), safe(characters), ag.getLabel(), ag.getGuide(), direction);

        JsonNode json = generateJsonRetry(prompt);
        return new StoryOutline(
                json.path("title").asText("%s 이야기".formatted(theme.getLabel())),
                json.path("synopsis").asText(""));
    }

    @Override
    public List<BookPageDraft> pages(StoryTheme theme, String characters, AgeGroup ageGroup,
                                     StoryOutline outline, int pageCount) {
        AgeGroup ag = ageGroup != null ? ageGroup : AgeGroup.AGE_5_6;
        String prompt = """
                아래 동화를 그림책 %d페이지로 나눠줘.
                - 제목: %s
                - 줄거리: %s
                - 주제: %s / 등장인물: %s
                - 대상 연령: %s / 텍스트 작성 지침(반드시 지킬 것): %s

                중요(옷 연출): 주인공이 "본인"이라는 느낌이 강하게 들도록,
                - 처음 2~3페이지는 아이가 "평상복(everyday, 자기 실제 옷)"을 입고 집/방 등 일상에서 시작.
                - 3~4페이지쯤 자연스럽게 옷을 갈아입거나 마법으로 "%s" 주제에 어울리는 특별한 옷으로 변신하는 장면을 넣어줘.
                - 그 이후 페이지는 주제 의상으로 모험.

                매우 중요(인물 고정): 모든 페이지의 text와 scene에는 위 "등장인물" 목록의 인물만 등장해야 한다.
                목록에 없는 다른 아이·친구·형제·사람을 절대 추가하지 마. scene 영문에도 목록의 인물만 묘사할 것
                (예: 주인공이 1명이면 scene에 "two children"처럼 두 명이 나오게 쓰지 말고 그 1명만 묘사).

                각 페이지 객체는 세 필드를 가진다:
                  "text": 위 "텍스트 작성 지침"을 반드시 따른 한국어 이야기 문구
                  "scene": 삽화 생성용 장면 설명(영문, 배경/동작/의상 포함, 한 문장). 위 등장인물만 등장, 다른 사람 금지.
                  "outfit": "everyday" 또는 "costume" (전환 전=everyday, 전환 후=costume)
                처음-중간-끝의 흐름을 갖추고 마지막은 따뜻하게 마무리. 정확히 %d개.
                반드시 JSON만: {"pages": [{"text": "...", "scene": "...", "outfit": "everyday"}, ...]}
                """.formatted(pageCount, safe(outline.title()), safe(outline.synopsis()),
                theme.getLabel(), safe(characters), ag.getLabel(), ag.getGuide(), theme.getLabel(), pageCount);

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
