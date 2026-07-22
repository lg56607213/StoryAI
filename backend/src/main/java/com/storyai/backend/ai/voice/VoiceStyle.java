package com.storyai.backend.ai.voice;

/**
 * 한 목소리 캐스팅의 실제 합성 파라미터.
 * - voiceName: Gemini TTS 프리빌트 보이스 이름(예: Leda, Puck, Sulafat)
 * - style    : 말투 지시(한국어 자연어, 예: "따뜻하고 다정하게"). 비어있으면 평범하게 읽는다.
 */
public record VoiceStyle(String voiceName, String style) {
}
