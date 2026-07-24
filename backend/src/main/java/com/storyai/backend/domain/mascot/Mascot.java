package com.storyai.backend.domain.mascot;

import com.storyai.backend.domain.videojob.StoryTheme;

/**
 * "히어로 친구들" — 투데이히어로 고유 마스코트(브랜드 IP).
 * 주제마다 어울리는 한 친구가 아이의 단짝으로 이야기 내내 함께한다.
 * (뜬금없는 조력자 등장 대신, 처음부터 소개되고 끝까지 일관되게 나오는 고정 캐릭터)
 *
 * - appearance: 삽화 생성용 외형 스펙(영문). 시트를 1회 생성해 캐시하므로 모든 책에서 같은 모습이 나온다.
 * - personality: 스토리 프롬프트에 넣는 성격·역할(한국어).
 * - voice: 낭독 목소리 캐스팅 코드({@link com.storyai.backend.ai.story.VoiceCast})
 */
public enum Mascot {

    BYEOL("별이", "유니콘", "unicorn",
            "a small chubby baby unicorn with a soft white coat, a flowing pastel rainbow mane and tail "
                    + "(soft pink, mint and lavender), a small glowing star-shaped horn on its forehead, "
                    + "big gentle eyes and tiny golden hooves",
            "다정하고 따뜻한 길잡이. 아이가 망설일 때 용기를 북돋아 준다.",
            "fairy"),

    BURI("부리", "아기 드래곤", "dragon",
            "a small round baby dragon with mint-green scales, a cream-colored belly, tiny stubby wings, "
                    + "two small friendly horns, big round eyes, and glittering sparkles instead of fire "
                    + "puffing from its mouth",
            "겁이 조금 많지만 결정적인 순간에 가장 용감해지는 친구.",
            "small_animal"),

    KKAMNYANG("깜냥", "아기 부엉이", "owl",
            "a small fluffy baby owl with grey-brown feathers, large round eyes with light ring markings "
                    + "that look like glasses, a tiny tuft of feathers on its head, and a little twig staff",
            "아는 것이 많은 조언자. 힌트와 재미있는 수수께끼로 길을 알려 준다.",
            "elder"),

    PADO("파도", "아기 돌고래", "dolphin",
            "a small cheerful baby dolphin with light sky-blue skin, a pale cream belly, a small "
                    + "shell-shaped marking on its back, and a always-smiling mouth",
            "명랑하고 헤엄을 잘 친다. 앞장서서 길을 안내한다.",
            "small_animal"),

    CHOCO("초코", "아기 원숭이", "monkey",
            "a small playful baby monkey with soft brown fur, a cream face and belly, a bright yellow "
                    + "scarf around its neck, and a long curly tail",
            "장난기 많은 단짝. 먹을 것과 숨은 길을 잘 찾아낸다.",
            "child_boy");

    private final String koreanName;
    private final String species;
    /** 영문 장면 설명에서 이 친구가 등장하는지 판별할 키워드(unicorn/dragon/owl/...). */
    private final String enKeyword;
    private final String appearance;
    private final String personality;
    private final String voice;

    Mascot(String koreanName, String species, String enKeyword, String appearance,
           String personality, String voice) {
        this.koreanName = koreanName;
        this.species = species;
        this.enKeyword = enKeyword;
        this.appearance = appearance;
        this.personality = personality;
        this.voice = voice;
    }

    public String getEnKeyword() {
        return enKeyword;
    }

    /** 이 장면 설명에 단짝 친구가 등장하는가(영문 종류 키워드 또는 한글 이름). */
    public boolean mentionedIn(String scene) {
        if (scene == null || scene.isBlank()) {
            return false;
        }
        String s = scene.toLowerCase();
        return s.contains(enKeyword) || scene.contains(koreanName);
    }

    public String getKoreanName() {
        return koreanName;
    }

    public String getSpecies() {
        return species;
    }

    public String getAppearance() {
        return appearance;
    }

    public String getPersonality() {
        return personality;
    }

    public String getVoice() {
        return voice;
    }

    /** 삽화 참조에 쓸 짧은 설명(영문): "별이, a small chubby baby unicorn ...". */
    public String describeForIllustration() {
        return koreanName + ", " + appearance;
    }

    /** 주제에 어울리는 단짝 친구. 모든 주제가 한 친구에 매핑된다. */
    public static Mascot forTheme(StoryTheme theme) {
        if (theme == null) {
            return BYEOL;
        }
        return switch (theme) {
            // 배경·모험
            case PRINCESS, PRINCE, SPACE -> BYEOL;
            case HERO, ADVENTURE, DINOSAUR -> BURI;
            case ANIMAL_FRIENDS, FOREST -> KKAMNYANG;
            case OCEAN, PIRATE -> PADO;
            case JUNGLE -> CHOCO;

            // 생활 습관 — 상황에 어울리는 친구를 붙인다.
            case BRUSHING_TEETH, HOSPITAL -> BURI;          // 무서운 순간에 용기를 주는 친구
            case BATH_TIME, WATER_PLAY, SWIMMING -> PADO;   // 물에서 앞장서는 친구
            case HANGEUL, STUDY_HABIT -> KKAMNYANG;         // 알려주고 이끌어 주는 조언자
            case SLEEP_ALONE, NEW_SIBLING -> BYEOL;         // 다정하게 마음을 달래주는 친구
            case EATING_WELL, FIRST_DAY -> CHOCO;           // 함께 놀고 먹는 단짝
        };
    }
}
