package com.storyai.backend.domain.videojob;

/** 영상 산출물의 애니메이션 스타일. */
public enum VideoStyle {
    ANIM_2D("2D 애니메이션"),
    ANIM_3D("3D 애니메이션(베베핀풍)");

    private final String label;

    VideoStyle(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
