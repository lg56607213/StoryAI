package com.storyai.backend.domain.videojob;

/** 대상 연령대. 연령에 맞춰 텍스트 분량·의성어/의태어 사용을 조절한다(guide). */
public enum AgeGroup {
    AGE_3_4("3~4세",
            "페이지당 2~3문장(약 25~50자). 아주 쉬운 단어만 쓰되 문장이 앙상하지 않게 살을 붙여 다정하게. "
            + "의성어·의태어를 페이지마다 2개 이상 풍부하게 넣기(반짝반짝, 콩콩, 폴짝폴짝, 데굴데굴, 퐁당, "
            + "살랑살랑, 방긋방긋, 두근두근 등), 되도록 문장 앞에 두고 리듬을 반복해 노래하듯 읽히게. "
            + "짧은 감탄사(우아!, 와!)와 다정한 말투로 아이가 몰입하게."),
    AGE_5_6("5~6세",
            "페이지당 2~3문장(약 40~70자). 쉽고 친근한 단어. "
            + "의성어·의태어를 페이지마다 1~2개 자연스럽게 섞고, 인물의 감정과 짧은 대사를 더해 생동감 있게."),
    AGE_7_8("7~8세",
            "페이지당 2~3문장(약 45~80자). 원인-결과와 간단한 대화·감정 묘사를 넣는다. "
            + "의성어·의태어는 강조할 때만 가끔(4~5페이지에 1회 정도)."),
    AGE_9_10("9~10세",
            "페이지당 3~4문장(약 80~130자). 풍부한 어휘와 비유, 인물의 마음(내면) 묘사, 서사의 흐름을 살린다. "
            + "의성어·의태어는 거의 쓰지 않고 절제한다.");

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
