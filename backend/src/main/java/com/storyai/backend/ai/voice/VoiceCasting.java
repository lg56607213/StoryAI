package com.storyai.backend.ai.voice;

import com.storyai.backend.ai.story.VoiceCast;
import org.springframework.stereotype.Component;

/**
 * 낭독 목소리 캐스팅 → 실제 Gemini TTS 보이스/말투로 매핑한다.
 * 보이스 이름은 Gemini TTS 프리빌트 보이스(youthful=Leda, upbeat=Puck, warm=Sulafat,
 * lively=Sadachbia, gravelly=Algenib, mature=Gacrux, soft=Achernar 등)를 사용.
 *
 * 나중에 NARRATOR는 부모 목소리(ElevenLabs)로 대체된다(캐스팅 밖에서 처리).
 */
@Component
public class VoiceCasting {

    /**
     * @param voiceHint 세그먼트의 목소리 코드(narrator/hero/small_animal 등)
     * @param protagonistDescription 주인공 설명(hero 성별 추정용, null 허용)
     */
    public VoiceStyle resolve(String voiceHint, String protagonistDescription) {
        VoiceCast cast = VoiceCast.from(voiceHint);
        return switch (cast) {
            case NARRATOR -> new VoiceStyle("Sulafat", "따뜻하고 다정하게, 동화를 읽어주듯 천천히");
            case HERO -> heroVoice(protagonistDescription);
            case CHILD_GIRL -> new VoiceStyle("Leda", "귀엽고 명랑한 여자아이 목소리로");
            case CHILD_BOY -> new VoiceStyle("Puck", "장난기 있는 밝은 남자아이 목소리로");
            case SMALL_ANIMAL -> new VoiceStyle("Sadachbia", "작고 귀여운 동물처럼 높고 발랄하게");
            case BIG_ANIMAL -> new VoiceStyle("Algenib", "크고 힘센 동물처럼 굵고 우렁차게");
            case ELDER -> new VoiceStyle("Gacrux", "지혜로운 어른처럼 차분하고 낮게");
            case FAIRY -> new VoiceStyle("Achernar", "요정처럼 맑고 부드럽게");
        };
    }

    /** 주인공 대사: 설명에 남아 힌트가 있으면 남자아이, 아니면 밝은 여자아이(기본)로 캐스팅. */
    private VoiceStyle heroVoice(String protagonistDescription) {
        String d = protagonistDescription == null ? "" : protagonistDescription.toLowerCase();
        boolean boy = d.contains("남자") || d.contains("남아") || d.contains("소년")
                || d.contains("아들") || d.contains("boy") || d.contains("male")
                || d.contains("son");
        return boy
                ? new VoiceStyle("Puck", "밝고 씩씩한 어린 남자아이 목소리로")
                : new VoiceStyle("Leda", "밝고 씩씩한 어린 여자아이 목소리로");
    }
}
