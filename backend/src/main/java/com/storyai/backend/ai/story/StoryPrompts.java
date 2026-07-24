package com.storyai.backend.ai.story;

import com.storyai.backend.domain.mascot.Mascot;
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

    public static String outline(StoryTheme theme, String themeLabel, String characters,
                                 AgeGroup ag, String storyDirection) {
        String direction = (storyDirection == null || storyDirection.isBlank())
                ? "특별한 요청 없음 (주제에 맞게 자유롭게)" : storyDirection;
        Mascot m = Mascot.forTheme(theme);
        return """
                너는 그림동화 작가야. 아래 조건으로 따뜻하고 안전한 한국어 동화의 "탄탄한 줄거리"를 만들어줘.
                - 주제: %s
                - 주인공(들): %s
                - 대상 연령: %s (%s)
                - 스토리 방향(고객 요청): %s
                - 단짝 친구(반드시 등장): %s (%s) — %s
                %s

                단짝 친구 규칙(매우 중요): 이 동화에는 "%s"(이)라는 %s 친구가 **반드시** 등장한다.
                이야기 초반에 자연스럽게 만나(또는 처음부터 함께 있고), 끝까지 주인공 곁에서 함께한다.
                이 친구 외에 비슷한 역할의 새로운 조력자 동물을 또 만들지 마라(친구는 이 하나로 충분하다).

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
                """.formatted(safe(themeLabel), safe(characters), ag.getLabel(), ag.getGuide(), direction,
                m.getKoreanName(), m.getSpecies(), m.getPersonality(),
                habitBlock(theme),
                m.getKoreanName(), m.getSpecies());
    }

    /** 생활 습관 주제면 "문제→어려움→스스로 해냄" 뼈대를 이야기에 강하게 주입한다. BACKGROUND면 빈 문자열. */
    private static String habitBlock(StoryTheme theme) {
        if (theme == null || theme.getStoryGuide() == null) {
            return "";
        }
        return """
                - 이야기 뼈대(생활 습관 주제, 반드시 이 흐름을 따를 것): %s
                이 주제는 모험 판타지가 아니라 아이의 "실제 생활 습관" 이야기다.
                주인공이 그 일을 '싫어하거나 무서워하는 마음'에서 시작해, 작은 실패·눈물도 겪지만,
                단짝 친구와 가족의 도움으로 용기를 내어 '스스로 해내는' 성장으로 끝나야 한다.
                교훈을 설교하듯 말하지 말고, 인물의 감정과 행동으로 자연스럽게 느끼게 하라.
                마법 변신이나 다른 세계로의 이동은 넣지 말고, 아이의 일상 공간(집·욕실·치과·교실 등)에서 진행한다.
                """.formatted(theme.getStoryGuide());
    }

    public static String pages(StoryTheme theme, String themeLabel, String characters, AgeGroup ag,
                               StoryOutline outline, int pageCount) {
        Mascot m = Mascot.forTheme(theme);
        return """
                아래 동화를 그림책 %d페이지로 나눠줘.
                - 제목: %s
                - 줄거리: %s
                - 주제: %s / 등장인물: %s
                - 대상 연령: %s / 텍스트 작성 지침(반드시 지킬 것): %s
                - 단짝 친구(반드시 등장): %s (%s) — %s

                단짝 친구 규칙(매우 중요): "%s"(은)는 이 동화의 고정 친구다.
                초반에 자연스럽게 등장시키고 끝까지 함께하며, 여러 페이지에서 주인공과 직접 대화하게 하라.
                이 친구의 대사 세그먼트는 voice 코드로 반드시 "%s" 를 쓰고, speaker 는 "%s" 로 적어라.
                이 친구가 나오는 페이지의 영문 "scene"에는 반드시 이 친구를 묘사해 넣어라
                (이렇게 묘사할 것: %s).
                이 친구 말고 비슷한 역할의 새로운 조력자 동물을 추가로 만들지 마라.

                %s

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

                가장 중요(사건 나열 금지 · 인물 중심으로 쓰기):
                "무엇이 일어났는지"를 설명하지 말고, "인물이 그때 어떻게 느끼고 무엇을 했는지"를 보여줘라.
                - 나쁜 예(사건 나열): "소영이와 상우가 길을 가다가 길을 잃은 아기곰을 발견했어요."
                - 좋은 예(인물 중심): "상우가 돌부리에 걸려 넘어졌어요. \\"으앙!\\" 상우가 울음을 터뜨리자,
                  소영이가 얼른 달려와 무릎을 호호 불어 주었어요."
                각 페이지에는 반드시 다음이 드러나야 한다:
                  1) 인물의 '감정'(기쁨·놀람·걱정·부끄러움·용기 등)
                  2) 그 감정에서 나온 '구체적인 행동'(달려간다, 손을 잡는다, 뒤로 숨는다, 어깨를 토닥인다 등)
                  3) 인물 사이의 '주고받음'(도와주기·위로하기·같이 웃기·나눠주기)
                주인공이 늘 이야기의 중심에 있어야 하고, 배경·사물 묘사에 페이지를 낭비하지 마라.
                감정은 페이지마다 조금씩 달라져 이야기에 오르내림이 있어야 한다(계속 즐겁기만 하면 안 된다).

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
                  "scene": 삽화 생성용 장면 설명(영문, 한두 문장). 아래 "scene 작성법"을 반드시 따를 것.
                  "outfit": "everyday" 또는 "costume" (전환 전=everyday, 전환 후=costume)

                scene 작성법(매우 중요 — 정적인 그림이 나오지 않게):
                인물이 그냥 "서 있는" 장면을 쓰지 마라. 반드시 아래 3가지를 영문으로 담아라.
                  1) 표정(facial expression): 예 crying with scrunched-up eyes, eyes wide with surprise,
                     beaming with a proud smile, cheeks flushed, worried frown
                  2) 몸짓·자세(body language / action): 예 kneeling down to hug, reaching out a hand,
                     crouching beside, tumbling forward, holding hands, hiding behind
                  3) 인물 사이의 관계·거리(interaction): 누가 누구를 향해 무엇을 하고 있는지
                카메라는 인물을 크게 담아라(close or medium shot). 배경은 분위기만 거들 뿐 주인공이 화면의 중심이다.
                  - 나쁜 예: "Soyoung and Sangwoo standing in a forest with a bear cub."
                  - 좋은 예: "Close view of Sangwoo sitting on the path crying with scrunched-up eyes and a
                    scraped knee, while Soyoung kneels beside him, gently blowing on his knee with a worried
                    but caring expression, one hand on his shoulder; soft sunlit forest path blurred behind them."
                처음-중간-끝의 흐름을 갖추고 마지막은 따뜻하게 마무리. 정확히 %d개.
                반드시 JSON만: {"pages": [{"segments": [{"role":"narration","text":"..."}, {"role":"dialogue","speaker":"토끼","voice":"small_animal","text":"\\"안녕!\\""}], "scene": "...", "outfit": "everyday"}, ...]}
                """.formatted(pageCount, safe(outline.title()), safe(outline.synopsis()),
                safe(themeLabel), safe(characters), ag.getLabel(), ag.getGuide(),
                m.getKoreanName(), m.getSpecies(), m.getPersonality(),
                m.getKoreanName(), m.getVoice(), m.getKoreanName(), m.getAppearance(),
                outfitBlock(theme, themeLabel), pageCount);
    }

    /**
     * 옷 연출 지시. 배경·모험 주제는 "평상복 → 주제 의상 변신", 생활 습관 주제는 변신 없이 일상 옷차림 유지.
     */
    private static String outfitBlock(StoryTheme theme, String themeLabel) {
        boolean transition = theme == null || theme.usesCostumeTransition();
        if (transition) {
            return """
                중요(옷 연출): 주인공이 "본인"이라는 느낌이 강하게 들도록,
                - 처음 2~3페이지는 아이가 "평상복(everyday, 자기 실제 옷)"을 입고 집/방 등 일상에서 시작.
                - 3~4페이지쯤 자연스럽게 옷을 갈아입거나 마법으로 "%s" 주제에 어울리는 특별한 옷으로 변신하는 장면을 넣어줘.
                - 그 이후 페이지는 주제 의상으로 모험. (그 페이지들의 outfit은 "costume")
                """.formatted(safe(themeLabel));
        }
        return """
                중요(옷차림): 이 이야기는 생활 습관 주제라 마법 변신이 없다.
                아이는 처음부터 끝까지 이 활동에 어울리는 한 가지 옷차림(예: 잠옷·수영복·목욕수건·평상복)을 유지한다.
                모든 페이지의 outfit 값은 "costume" 으로 둔다(이 값이 활동용 옷차림 캐릭터 시트를 가리킨다). 변신 장면은 넣지 마라.
                """;
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }
}
