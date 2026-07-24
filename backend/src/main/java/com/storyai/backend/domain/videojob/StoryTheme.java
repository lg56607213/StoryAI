package com.storyai.backend.domain.videojob;

import com.storyai.backend.domain.storycharacter.CharacterRole;

/**
 * 스토리 주제. 두 갈래로 나뉜다.
 * - BACKGROUND(배경·모험): 공주·용사·우주처럼 세계관 안에서 모험한다. 주제 의상으로 갈아입는 연출이 어울린다.
 * - HABIT(생활 습관): 양치·목욕·한글처럼 아이의 실제 생활 문제를 다룬다.
 *   변신 연출 없이 일상 옷차림을 유지하고, "싫어함 → 어려움 → 스스로 해냄"의 성장 구조를 따른다.
 */
public enum StoryTheme {

    // ---------- 배경·모험 ----------
    PRINCESS("공주", Category.BACKGROUND, null),
    PRINCE("왕자", Category.BACKGROUND, null),
    HERO("용사", Category.BACKGROUND, null),
    ADVENTURE("모험", Category.BACKGROUND, null),
    ANIMAL_FRIENDS("동물 친구", Category.BACKGROUND, null),
    SPACE("우주 탐험", Category.BACKGROUND, null),
    DINOSAUR("공룡 나라", Category.BACKGROUND, null),
    OCEAN("바닷속", Category.BACKGROUND, null),
    FOREST("숲속 친구들", Category.BACKGROUND, null),
    JUNGLE("정글 탐험", Category.BACKGROUND, null),
    PIRATE("해적 모험", Category.BACKGROUND, null),

    // ---------- 생활 습관 ----------
    BRUSHING_TEETH("양치하기", Category.HABIT,
            "양치를 싫어하고 미루던 아이가, 이에 남은 음식 찌꺼기 때문에 충치 세균에게 이가 공격받는 일을 겪는다. "
                    + "이가 아파 치과에 가서 무섭고 아픈 치료를 받고 눈물도 흘리지만, 용기를 내어 견뎌낸다. "
                    + "그 뒤로는 스스로 구석구석 양치하게 되고, 다음 검진에서 칭찬을 받는다."),
    BATH_TIME("목욕하기", Category.HABIT,
            "물이 무섭고 머리 감기를 싫어하던 아이가, 하루 종일 신나게 놀아 온몸이 끈적끈적해진다. "
                    + "따뜻한 물과 보글보글 거품이 생각보다 포근하다는 걸 알게 되고, 스스로 몸을 씻는다. "
                    + "뽀송해진 기분이 얼마나 좋은지 느끼며 마무리한다."),
    HANGEUL("한글 공부", Category.HABIT,
            "글자가 어렵고 지겹던 아이가, 자기 이름의 글자를 처음 알아보며 눈이 반짝인다. "
                    + "삐뚤빼뚤 쓰다가 틀리기도 하고 속상해하지만 포기하지 않는다. "
                    + "마침내 가족에게 짧은 편지를 써서 건네고, 글자가 마음을 전한다는 걸 알게 된다."),
    STUDY_HABIT("공부 습관", Category.HABIT,
            "놀고 싶은 마음에 자꾸 미루던 아이가, 해야 할 일이 쌓여 마음이 무거워진다. "
                    + "작은 것부터 하나씩 해보자는 제안을 받아들여 조금씩 집중해 본다. "
                    + "다 끝냈을 때의 후련함과 뿌듯함을 느끼고, 스스로 계획을 세우게 된다."),
    WATER_PLAY("물놀이", Category.HABIT,
            "물이 얼굴에 닿는 게 무섭던 아이가, 얕은 물에서 발끝부터 조금씩 익숙해진다. "
                    + "안전 약속(어른과 함께, 준비운동)을 지키며 용기를 낸다. "
                    + "물장구를 치며 신나게 웃고, 물이 무섭지만은 않다는 걸 알게 된다."),
    SWIMMING("수영 배우기", Category.HABIT,
            "물에 뜨는 것이 무서워 벽만 붙잡던 아이가, 숨 참기와 발차기를 하나씩 연습한다. "
                    + "물을 먹고 당황해 울컥하기도 하지만 다시 도전한다. "
                    + "마침내 혼자 몇 걸음 나아가며 '해냈다'는 기쁨을 느낀다."),
    SLEEP_ALONE("혼자 자기", Category.HABIT,
            "어두운 방이 무서워 혼자 못 자던 아이가, 밤에 들리는 소리들의 정체를 하나씩 알아간다. "
                    + "무서움을 솔직히 말하고 위로를 받으며 마음이 놓인다. "
                    + "작은 불빛과 인형과 함께 스스로 잠드는 데 성공한다."),
    EATING_WELL("골고루 먹기", Category.HABIT,
            "채소를 싫어해 자꾸 남기던 아이가, 음식마다 몸에서 하는 일이 다르다는 걸 알게 된다. "
                    + "한 입만 도전해 보고, 생각보다 괜찮다는 걸 발견한다. "
                    + "골고루 먹고 힘이 나서 신나게 뛰어노는 것으로 마무리한다."),
    FIRST_DAY("어린이집 첫날", Category.HABIT,
            "낯선 곳이 무섭고 헤어지기 싫어 울먹이던 아이가, 용기를 내어 인사를 건넨다. "
                    + "함께 놀 친구가 생기고 하루가 즐거워진다. "
                    + "집에 돌아와 오늘 있었던 일을 신나게 이야기한다."),
    HOSPITAL("병원 가기", Category.HABIT,
            "주사와 병원이 무서워 숨던 아이가, 아픈 곳을 낫게 해주는 곳이라는 걸 알게 된다. "
                    + "무서워서 눈물이 나지만 손을 꼭 잡고 용기를 낸다. "
                    + "잘 참아냈다는 칭찬을 받고 스스로가 대견해진다."),
    NEW_SIBLING("동생이 생겼어요", Category.HABIT,
            "동생에게 관심을 빼앗긴 것 같아 서운하고 심술이 나던 아이가, 복잡한 마음을 솔직히 털어놓는다. "
                    + "여전히 사랑받고 있다는 걸 확인하고 마음이 풀린다. "
                    + "동생을 돌보며 형·누나로서의 뿌듯함을 느낀다.");

    /** 주제 갈래 — 화면에서 그룹으로 나눠 보여주고, 이야기·의상 연출도 달라진다. */
    public enum Category {
        BACKGROUND("배경·모험"),
        HABIT("생활 습관");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private final String label;
    private final Category category;
    /** HABIT 주제의 이야기 뼈대(문제 → 어려움 → 스스로 해냄). BACKGROUND는 null. */
    private final String storyGuide;

    StoryTheme(String label, Category category, String storyGuide) {
        this.label = label;
        this.category = category;
        this.storyGuide = storyGuide;
    }

    public String getLabel() {
        return label;
    }

    public Category getCategory() {
        return category;
    }

    public String getStoryGuide() {
        return storyGuide;
    }

    /** 생활 습관 주제는 변신 연출 없이 일상 옷차림을 유지한다. */
    public boolean usesCostumeTransition() {
        return category == Category.BACKGROUND;
    }

    /** 고객이 직접 입력한 주제일 때의 특별 의상 — 주제 문구에 어울리게 맡긴다. */
    public static String costumeForCustom(String customTheme) {
        return "a charming children's-book outfit that clearly fits this story theme: \"" + customTheme + "\"";
    }

    /** 주제에 어울리는 의상. PRINCESS/PRINCE만 역할(성별)에 따라 달라진다. */
    public String costumeFor(CharacterRole role) {
        boolean main = role == CharacterRole.MAIN;
        return switch (this) {
            case PRINCESS -> main
                    ? "royal storybook attire that suits THIS child's apparent gender shown in the reference: "
                        + "if the child looks like a girl, a pretty pastel princess dress with a small tiara and a floral headband; "
                        + "if the child looks like a boy, a handsome little-prince royal outfit with a cape and a small crown "
                        + "(never put a boy in a dress)"
                    : "a cute royal outfit with a small crown, suited to the child";
            case PRINCE -> "a cute little-prince royal outfit with a cape and a small crown";
            case HERO -> "a brave little hero costume with a small cape";
            case ADVENTURE -> "a cute explorer outfit with an adventure vest, hat and a small backpack";
            case ANIMAL_FRIENDS -> "a cozy animal-eared hoodie costume";
            case SPACE -> "a cute little astronaut space suit";
            case DINOSAUR -> "a fun dinosaur explorer outfit with a dino-hood hat and an adventure vest";
            case OCEAN -> "a cute diver or sailor outfit with a life vest";
            case FOREST -> "a woodland ranger outfit with a leafy cape";
            case JUNGLE -> "a safari explorer outfit with a hat and binoculars";
            case PIRATE -> "a friendly little pirate outfit with a bandana and a toy sword";

            // 생활 습관: 변신이 아니라 그 상황에 자연스러운 옷차림.
            case BRUSHING_TEETH, SLEEP_ALONE -> "comfy everyday pajamas";
            case BATH_TIME -> "a soft bath towel wrapped around, or a cozy bathrobe";
            case WATER_PLAY, SWIMMING -> "a cute swimsuit with a swim cap";
            case FIRST_DAY -> "neat everyday clothes with a small kindergarten backpack";
            case HANGEUL, STUDY_HABIT, EATING_WELL, HOSPITAL, NEW_SIBLING ->
                    "the child's own everyday clothes, unchanged";
        };
    }
}
