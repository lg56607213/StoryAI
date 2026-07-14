package com.storyai.backend.domain.videojob;

/** 대상 연령대. 연령에 맞춰 텍스트 분량·의성어/의태어 사용을 조절한다(guide). */
public enum AgeGroup {
    AGE_3_4("3~4세",
            "페이지당 딱 1문장, 아주 짧게(약 6~18자). 아주 쉬운 명사·동사만(어려운 단어 금지). "
            + "거의 모든 페이지에 의성어·의태어를 1개 이상 넣기(반짝반짝, 콩콩, 폴짝폴짝, 데굴데굴, 퐁당 등), "
            + "가능하면 문장 맨 앞에 두고 같은 리듬을 반복해 노래하듯이."),
    AGE_5_6("5~6세",
            "페이지당 1~2문장(약 20~40자). 쉽고 친근한 단어. "
            + "의성어·의태어를 자주(2~3페이지마다 최소 1회) 자연스럽게 섞고, 간단한 감정 표현을 더한다."),
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
