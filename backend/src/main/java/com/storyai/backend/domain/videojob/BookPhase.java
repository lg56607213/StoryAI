package com.storyai.backend.domain.videojob;

/**
 * 책 생성 단계.
 * - PREVIEW: 표지+헌정+앞 몇 장만 생성해 미리 보여준다(비용 최소화).
 * - FULL: 고객이 확인/결제한 뒤 나머지 전체를 생성한다.
 * 영상(VIDEO)은 미리보기 개념이 없어 항상 FULL로 둔다.
 */
public enum BookPhase {
    PREVIEW,
    FULL
}
