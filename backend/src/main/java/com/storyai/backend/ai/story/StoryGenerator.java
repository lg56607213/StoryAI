package com.storyai.backend.ai.story;

import com.storyai.backend.domain.videojob.AgeGroup;
import com.storyai.backend.domain.videojob.StoryTheme;

import java.util.List;

/**
 * 스토리(글) 생성기. 벤더 교체가 쉽도록 인터페이스로 추상화한다.
 * 현재 구현: Gemini. (나중에 Claude 등으로 교체 가능)
 */
public interface StoryGenerator {

    /** API 키 등 설정이 되어 실제 생성이 가능한지. false면 호출부는 더미로 폴백한다. */
    boolean isAvailable();

    /**
     * 제목 + 줄거리 개요 생성. storyDirection(선택)이 있으면 이야기 틀에 반영.
     * themeLabel은 화면·프롬프트에 쓸 주제 문구(직접입력 주제면 그 문구), theme은 마스코트 매핑용 분류값.
     */
    StoryOutline outline(StoryTheme theme, String themeLabel, String characters,
                         AgeGroup ageGroup, String storyDirection);

    /** 개요를 정확히 pageCount개의 페이지(문구 + 삽화 장면)로 확장. 연령에 맞춰 텍스트 분량/의성어 조절. */
    List<BookPageDraft> pages(StoryTheme theme, String themeLabel, String characters, AgeGroup ageGroup,
                             StoryOutline outline, int pageCount);
}
