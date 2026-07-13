package com.storyai.backend.domain.videojob;

/** 대상 연령대. 연령에 맞춰 텍스트 분량·의성어/의태어 사용을 조절한다(guide). */
public enum AgeGroup {
    AGE_3_4("3~4세", "문장을 아주 짧게(페이지당 1문장), 아주 쉬운 단어만 사용. "
            + "의성어·의태어를 많이 넣기(반짝반짝, 콩콩, 폴짝폴짝 등). 같은 리듬을 반복."),
    AGE_5_6("5~6세", "문장을 짧게(1~2문장), 쉬운 단어. 의성어·의태어를 꽤 사용. 밝고 리듬감 있게."),
    AGE_7_8("7~8세", "문장을 조금 더 길게(2~3문장). 약간의 서사와 감정 표현. 의성어·의태어는 적당히."),
    AGE_9_10("9~10세", "문장을 더 풍부하게(3~4문장). 어휘·서사·감정을 더 깊이. 의성어·의태어는 절제.");

    private final String label;
    private final String guide;

    AgeGroup(String label, String guide) {
        this.label = label;
        this.guide = guide;
    }

    public String getLabel() {
        return label;
    }

    public String getGuide() {
        return guide;
    }
}
