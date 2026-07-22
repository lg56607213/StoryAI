package com.storyai.backend.ai.story;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * pages() JSON 응답을 BookPageDraft 목록으로 파싱하고 개수를 보정한다.
 * Gemini·Claude 생성기가 동일한 파싱 로직을 쓰도록 단일 소스로 둔다.
 *
 * 페이지 JSON은 세그먼트 기반이다:
 *   {"outfit":"everyday","scene":"...","segments":[
 *       {"role":"narration","text":"..."},
 *       {"role":"dialogue","speaker":"토끼","voice":"small_animal","text":"..."}]}
 * 인쇄용 text는 세그먼트 텍스트를 순서대로 이어 붙여 만든다(없으면 top-level "text" 폴백).
 */
public final class PageDraftParser {

    private PageDraftParser() {
    }

    public static List<BookPageDraft> parse(JsonNode json, int pageCount) {
        List<BookPageDraft> pages = new ArrayList<>();
        for (JsonNode p : json.path("pages")) {
            pages.add(parseOne(p));
        }
        // 개수가 모자라면 마지막으로 채우고, 넘치면 잘라 정확히 pageCount로 맞춘다.
        while (pages.size() < pageCount) {
            pages.add(pages.isEmpty() ? new BookPageDraft("", "", "costume") : pages.get(pages.size() - 1));
        }
        return pages.subList(0, pageCount);
    }

    private static BookPageDraft parseOne(JsonNode p) {
        String outfit = "everyday".equals(p.path("outfit").asText("costume")) ? "everyday" : "costume";
        String scene = p.path("scene").asText("");

        List<NarrationSegment> segments = new ArrayList<>();
        for (JsonNode s : p.path("segments")) {
            String text = s.path("text").asText("").strip();
            if (text.isEmpty()) {
                continue;
            }
            boolean dialogue = "dialogue".equals(s.path("role").asText("narration"));
            if (dialogue) {
                String speaker = s.path("speaker").asText("").strip();
                String voice = s.path("voice").asText("").strip();
                segments.add(new NarrationSegment("dialogue", speaker, voice.isEmpty() ? "hero" : voice, text));
            } else {
                segments.add(new NarrationSegment("narration", "", "narrator", text));
            }
        }

        String text = segments.isEmpty()
                ? p.path("text").asText("")
                : joinSegments(segments);
        return new BookPageDraft(text, scene, outfit, segments);
    }

    /** 인쇄용 본문: 세그먼트 텍스트를 공백으로 이어 붙인다(대사 따옴표는 세그먼트 텍스트에 이미 포함). */
    private static String joinSegments(List<NarrationSegment> segments) {
        StringBuilder sb = new StringBuilder();
        for (NarrationSegment seg : segments) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(seg.text());
        }
        return sb.toString();
    }
}
