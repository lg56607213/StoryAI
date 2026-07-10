package com.storyai.backend.domain.videojob;

/** 고객이 프로젝트마다 선택하는 최종 산출물 종류. */
public enum OutputType {
    BOOK("책"),
    VIDEO("영상");

    private final String label;

    OutputType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
