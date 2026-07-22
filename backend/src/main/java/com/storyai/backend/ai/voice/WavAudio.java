package com.storyai.backend.ai.voice;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * 원시 PCM(16-bit) 바이트에 WAV 헤더를 붙여주는 유틸.
 * Gemini TTS는 헤더 없는 raw PCM(기본 24kHz, 16-bit, mono)을 주므로,
 * ffmpeg가 바로 읽고 이어붙일 수 있도록 표준 WAV로 감싼다.
 */
public final class WavAudio {

    private WavAudio() {
    }

    public static byte[] pcm16ToWav(byte[] pcm, int sampleRate, int channels) {
        int bitsPerSample = 16;
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;
        int dataLen = pcm.length;

        ByteBuffer bb = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN);
        bb.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        bb.putInt(36 + dataLen);                 // ChunkSize
        bb.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        bb.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        bb.putInt(16);                           // Subchunk1Size (PCM)
        bb.putShort((short) 1);                  // AudioFormat = PCM
        bb.putShort((short) channels);
        bb.putInt(sampleRate);
        bb.putInt(byteRate);
        bb.putShort((short) blockAlign);
        bb.putShort((short) bitsPerSample);
        bb.put("data".getBytes(StandardCharsets.US_ASCII));
        bb.putInt(dataLen);
        bb.put(pcm);
        return bb.array();
    }
}
