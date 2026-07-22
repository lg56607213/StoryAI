package com.storyai.backend.ai.story;

import com.storyai.backend.domain.videojob.AgeGroup;
import com.storyai.backend.domain.videojob.StoryTheme;

/**
 * 스토리 생성 프롬프트(개요/페이지). Gemini·Claude 등 어떤 생성기든 동일 프롬프트를 쓰도록 단일 소스로 둔다.
 */
public final class StoryPrompts {

    private StoryPrompts() {
    }

    /** JSON만 출력하도록 강제하는 시스템 지시문(Claude 등에서 사용). */
    public static final String SYSTEM =
            "너는 따뜻하고 안전한 한국어 그림동화 작가야. 반드시 유효한 JSON만 출력해 — "
                    + "마크다운, 코드펜스(```), 부연 설명을 절대 붙이지 마.";

    public static String outline(StoryTheme theme, String characters, AgeGroup ag, String storyDirection) {
        String direction = (storyDirection == null || storyDirection.isBlank())
                ? "특별한 요청 없음 (주제에 맞게 자유롭게)" : storyDirection;
        return """
                너는 그림동화 작가야. 아래 조건으로 따뜻하고 안전한 한국어 동화의 "탄탄한 줄거리"를 만들어줘.
                - 주제: %s
                - 주인공(들): %s
                - 대상 연령: %s (%s)
                - 스토리 방향(고객 요청): %s

                반드시 "하나의 일관된 이야기"가 되도록 구성해:
                - 시작: 주인공의 상황과 '하나의 분명한 목표(또는 해결할 문제)'를 세운다.
                - 중간: 그 목표를 향한 여정에서 작은 어려움과 도움이 자연스럽게 이어진다.
                - 끝: 처음의 목표/문제가 따뜻하게 해결된다.
                일관성 규칙: 새로 등장하는 조력자·생물·사물은 반드시 이야기에 필요한 이유가 있고 끝까지 일관되게 쓰여야 한다.
                줄거리와 상관없는 요소를 뜬금없이 등장시키지 마(예: 무지개성을 찾는 이야기인데 관계없는 유니콘이 갑자기 나오는 식 금지).
                주인공 이름을 실제로 등장시키고, 폭력·공포는 금지.
                매우 중요(등장인물 고정): 사람 등장인물은 오직 위 "주인공(들)" 목록의 인물만 사용
                (목록에 없는 다른 사람 금지, 주제에 맞는 동물·상상 조력자는 이유가 있으면 허용).
                반드시 아래 JSON 형식으로만 답해:
                {"title": "동화 제목", "synopsis": "위 시작-중간-끝 흐름이 분명히 드러나는 6~8문장 줄거리(하나의 목표로 시작해 해결까지 일관되게)"}
                """.formatted(theme.getLabel(), safe(characters), ag.getLabel(), ag.getGuide(), direction);
    }

    public static String pages(StoryTheme theme, String characters, AgeGroup ag,
                               StoryOutline outline, int pageCount) {
        return """
                아래 동화를 그림책 %d페이지로 나눠줘.
                - 제목: %s
                - 줄거리: %s
                - 주제: %s / 등장인물: %s
                - 대상 연령: %s / 텍스트 작성 지침(반드시 지킬 것): %s

                중요(옷 연출): 주인공이 "본인"이라는 느낌이 강하게 들도록,
                - 처음 2~3페이지는 아이가 "평상복(everyday, 자기 실제 옷)"을 입고 집/방 등 일상에서 시작.
                - 3~4페이지쯤 자연스럽게 옷을 갈아입거나 마법으로 "%s" 주제에 어울리는 특별한 옷으로 변신하는 장면을 넣어줘.
                - 그 이후 페이지는 주제 의상으로 모험.

                매우 중요(이야기 일관성): 전체가 "하나의 이야기"로 처음-중간-끝이 자연스럽게 이어져야 한다.
                각 페이지는 앞 페이지에서 곧바로 이어지며, 같은 목표를 향해 나아간다.
                줄거리에 없는 새로운 캐릭터·생물·사물을 갑자기 등장시키지 마
                (예: 무지개성을 찾는 이야기인데 관계없는 유니콘이 뜬금없이 나오는 식 금지).
                조력자가 필요하면 줄거리에 맞게 미리 자연스럽게 등장시키고 끝까지 일관되게 쓴다.
                마지막 페이지는 처음에 세운 목표/문제를 따뜻하게 마무리한다.

                매우 중요(인물 고정): 모든 페이지의 대사와 scene에는 위 "등장인물" 목록의 인물만 등장해야 한다.
                목록에 없는 다른 아이·친구·형제·사람을 절대 추가하지 마. scene 영문에도 목록의 인물만 묘사할 것
                (예: 주인공이 1명이면 scene에 "two children"처럼 두 명이 나오게 쓰지 말고 그 1명만 묘사).
                단, 주제에 맞는 동물·상상 조력자(토끼, 사자, 부엉이, 요정 등)는 이야기에 필요하면 등장·대화할 수 있다.

                매우 중요(대사 비중): 이 동화는 "목소리로 읽어주는 영상"으로도 만들어진다.
                밋밋한 서술만 나열하지 말고, 주인공이 동물친구·조력자와 "직접 대화(대사)"하는 장면을 풍부하게 넣어라.
                전체적으로 서술과 대사의 비율이 대략 반반(5:5)이 되도록 하고, 대사는 짧고 생동감 있게.
                (단, 위 "텍스트 작성 지침"의 연령별 분량은 반드시 지켜라 — 대사를 늘리느라 페이지가 길어지면 안 된다.)

                각 페이지는 "segments"(낭독 조각) 배열로 본문을 표현한다. 각 조각은:
                  - 서술: {"role":"narration", "text":"한국어 서술 문구"}
                  - 대사: {"role":"dialogue", "speaker":"말하는 인물 이름", "voice":"목소리코드", "text":"\\"큰따옴표로 감싼 대사\\""}
                한 페이지 안에서 서술과 대사를 자연스럽게 번갈아 배치하고(예: 서술→대사→서술), 대사 text는 반드시 큰따옴표로 감싼다.

                "voice" 목소리코드는 인물의 특성에 맞게 아래 중 하나만 고른다:
                  - "narrator"     : 서술(항상 이 값). 이야기를 읽어주는 목소리.
                  - "hero"         : 주인공 본인의 대사.
                  - "child_girl"   : 여자아이 조연.
                  - "child_boy"    : 남자아이 조연.
                  - "small_animal" : 작고 귀여운 동물(토끼·다람쥐·아기새 등) — 밝고 높은 목소리.
                  - "big_animal"   : 크고 힘센 동물(사자·곰·호랑이 등) — 굵고 낮은 목소리.
                  - "elder"        : 지혜롭거나 나이 든 존재(부엉이·할아버지나무 등) — 차분한 어른.
                  - "fairy"        : 요정·마법 존재 — 맑은 여성 목소리.
                인물의 종류·성격에 맞춰 캐스팅해라(예: 토끼면 small_animal, 사자면 big_animal).

                각 페이지 객체는 다음 필드를 가진다:
                  "segments": 위 규칙의 낭독 조각 배열(서술+대사 번갈아, 대사 풍부하게)
                  "scene": 삽화 생성용 장면 설명(영문, 배경/동작/의상 포함, 한 문장). 위 등장인물만 등장.
                  "outfit": "everyday" 또는 "costume" (전환 전=everyday, 전환 후=costume)
                처음-중간-끝의 흐름을 갖추고 마지막은 따뜻하게 마무리. 정확히 %d개.
                반드시 JSON만: {"pages": [{"segments": [{"role":"narration","text":"..."}, {"role":"dialogue","speaker":"토끼","voice":"small_animal","text":"\\"안녕!\\""}], "scene": "...", "outfit": "everyday"}, ...]}
                """.formatted(pageCount, safe(outline.title()), safe(outline.synopsis()),
                theme.getLabel(), safe(characters), ag.getLabel(), ag.getGuide(), theme.getLabel(), pageCount);
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }
}
