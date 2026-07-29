package com.storyai.backend.video;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.storyai.backend.ai.GeminiClient;
import com.storyai.backend.ai.story.NarrationSegment;
import com.storyai.backend.ai.voice.VoiceCasting;
import com.storyai.backend.ai.voice.VoiceStyle;
import com.storyai.backend.ai.voice.WavAudio;
import com.storyai.backend.domain.bookpage.BookPage;
import com.storyai.backend.domain.bookpage.BookPageRepository;
import com.storyai.backend.domain.videojob.VideoJob;
import com.storyai.backend.domain.videojob.VideoJobRepository;
import com.storyai.backend.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 완성된 동화책(페이지 삽화 + 낭독 세그먼트)을 "읽어주는 영상(mp4)"으로 합성한다.
 * - 각 세그먼트를 캐스팅된 목소리로 TTS(Gemini) → 페이지 오디오로 이어붙임.
 * - 페이지 삽화를 16:9로 패딩 + 페이지 오디오 길이만큼 보여주는 클립을 ffmpeg로 만들고,
 *   모든 페이지 클립을 이어붙여 최종 mp4를 만든다.
 *
 * 서술(narrator)은 지금은 Gemini 기본 내레이터로 읽는다. 이후 부모 목소리(ElevenLabs)가 준비되면
 * narrator 세그먼트만 부모 목소리로 대체하도록 확장한다(캐스팅 밖에서 주입).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NarrationVideoService {

    private final BookPageRepository bookPageRepository;
    private final VideoJobRepository videoJobRepository;
    private final GeminiClient gemini;
    private final VoiceCasting casting;
    private final StorageService storage;
    private final ObjectMapper objectMapper;
    private final com.storyai.backend.ai.voice.ElevenLabsClient elevenLabs;
    private final com.storyai.backend.ai.voice.CloudTtsClient cloudTts;
    private final com.storyai.backend.notify.EmailNotifier emailNotifier;

    /** 이메일에 넣을 절대 URL의 기준(백엔드 공개 주소). 저장 파일은 /api/files/... 로 서빙된다. */
    @Value("${storyai.api-base-url:https://api.todayhero.co.kr}")
    private String apiBaseUrl;

    /** 재녹음 안내 링크용 프론트 주소(로그인 → 마이페이지에서 책 열어 재녹음). */
    @Value("${storyai.frontend-url:https://todayhero.co.kr}")
    private String frontendUrl;

    @Value("${storyai.video.ffmpeg-path:ffmpeg}")
    private String ffmpeg;
    @Value("${storyai.video.width:1280}")
    private int width;
    @Value("${storyai.video.height:720}")
    private int height;
    /** 세그먼트 사이에 넣는 자연스러운 쉼(밀리초). */
    @Value("${storyai.video.gap-ms:350}")
    private int gapMs;

    /** TTS 호출 사이 대기(ms) — 분당 요청 한도(속도제한) 회피용 스로틀. */
    @Value("${storyai.video.tts-throttle-ms:1500}")
    private int ttsThrottleMs;

    /** TTS 실패 시 재시도 전 대기(ms) — 점점 길게(분당 한도 리셋 대기). 첫 시도는 0. */
    private static final long[] TTS_RETRY_WAITS_MS = {0, 6000, 15000, 30000};

    /** ffmpeg 실행 가능 여부(배포 이미지에 설치돼 있는지). */
    public boolean isFfmpegAvailable() {
        try {
            Process p = new ProcessBuilder(ffmpeg, "-version").redirectErrorStream(true).start();
            boolean done = p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            return done && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** 비동기 트리거: 상태를 갱신하며 백그라운드로 영상을 만든다(미디어 전용 풀). */
    @Async("mediaTaskExecutor")
    public void generateAsync(Long jobId) {
        VideoJob job = videoJobRepository.findById(jobId).orElse(null);
        if (job == null) {
            return;
        }
        try {
            job.setNarrationVideoStatus("generating");
            videoJobRepository.save(job);

            // 부모 목소리를 쓸 예정이면, 실제로 그 목소리가 사용 가능한지 먼저 확인한다.
            // 쓸 수 없으면(녹음 처리 실패 등) 음성 없는 영상을 보내지 않고 "다시 녹음" 안내를 보낸다.
            String pv = job.getParentVoiceId();
            boolean parentIntended = pv != null && !pv.isBlank() && elevenLabs.isConfigured();
            if (parentIntended && !parentVoiceUsable(pv)) {
                job.setNarrationVideoStatus("voice_failed");
                videoJobRepository.save(job);
                emailNotifier.sendVoiceRerecord(job.getDeliveryEmail(), job.getGeneratedTitle(), rerecordUrl());
                log.warn("부모 목소리 사용 불가 → 재녹음 안내(음성 없는 영상 미발송) job={}", jobId);
                return;
            }

            String url = generate(job);
            job.setNarrationVideoUrl(url);
            job.setNarrationVideoStatus("ready");
            videoJobRepository.save(job);
            log.info("낭독 영상 생성 완료 job={} url={}", jobId, url);

            // 영상은 용량이 커서 첨부 대신 링크로 안내 메일을 보낸다.
            boolean parentVoice = job.getParentVoiceId() != null && !job.getParentVoiceId().isBlank();
            emailNotifier.sendVideoReady(job.getDeliveryEmail(), job.getGeneratedTitle(),
                    publicUrl(url), parentVoice);

            // 영상 완성 후 복제 음성 삭제(일랩 슬롯 확보 + 개인정보 파기). 재생성 시엔 기본 목소리/재녹음.
            cleanupParentVoice(job);
        } catch (Throwable e) {
            // Throwable까지: 메모리 부족(OutOfMemoryError) 등으로 죽어도 "만드는 중"에 갇히지 않고
            // 실패로 표시한다(사용자가 완성화면에서 다시 시도 가능).
            log.error("낭독 영상 생성 실패 job={}: {}", jobId, e.getMessage(), e);
            try {
                VideoJob fresh = videoJobRepository.findById(jobId).orElse(job);
                fresh.setNarrationVideoStatus("failed");
                videoJobRepository.save(fresh);
            } catch (Throwable ignore) {
                // 저장까지 실패하면 로그만 남긴다.
            }
        }
    }

    /** 영상 완성 후 복제 음성 자동 삭제 여부(일랩 슬롯 확보·개인정보 파기). */
    @Value("${storyai.video.delete-parent-voice-after:true}")
    private boolean deleteParentVoiceAfter;

    /**
     * 영상 완성 후 복제된 부모 목소리를 삭제한다(일랩 커스텀 음성 슬롯은 소수라 재사용 필요 + 개인정보 파기).
     * 삭제 후엔 parentVoiceId를 비워, 영상 재생성 시 기본 목소리로(또는 재녹음)로 진행되게 한다.
     */
    private void cleanupParentVoice(VideoJob job) {
        if (!deleteParentVoiceAfter) {
            return;
        }
        String pv = job.getParentVoiceId();
        if (pv == null || pv.isBlank()) {
            return;
        }
        try {
            elevenLabs.deleteVoice(pv);
            log.info("영상 완성 후 부모 목소리 삭제 job={} voice={}", job.getId(), pv);
        } catch (Exception e) {
            log.warn("부모 목소리 삭제 실패 job={}: {}", job.getId(), e.getMessage());
        }
        job.setParentVoiceId(null);
        job.setParentVoiceConsent(false);
        videoJobRepository.save(job);
    }

    /** 부모 목소리가 실제로 사용 가능한지 짧은 문장으로 시험 합성해 확인한다. */
    private boolean parentVoiceUsable(String voiceId) {
        try {
            byte[] test = elevenLabs.textToSpeechPcm("안녕하세요.", voiceId);
            return test != null && test.length > 0;
        } catch (Exception e) {
            log.warn("부모 목소리 확인 실패 voice={}: {}", voiceId, e.getMessage());
            return false;
        }
    }

    /** 재녹음 안내 링크(프론트 주소). */
    private String rerecordUrl() {
        return frontendUrl == null ? "" : frontendUrl.replaceAll("/+$", "");
    }

    /** 동기 생성: 최종 mp4를 저장하고 그 URL(/api/files/...)을 반환한다. */
    public String generate(VideoJob job) throws Exception {
        if (!gemini.isConfigured()) {
            throw new IllegalStateException("GEMINI_API_KEY 미설정 — TTS 불가");
        }
        if (!isFfmpegAvailable()) {
            throw new IllegalStateException("ffmpeg 사용 불가 — 배포 이미지에 ffmpeg가 설치돼야 함");
        }
        List<BookPage> pages = bookPageRepository.findByVideoJobIdOrderByPageNumberAsc(job.getId());
        if (pages.isEmpty()) {
            throw new IllegalStateException("페이지가 없음 job=" + job.getId());
        }

        Path dir = Files.createTempDirectory("narration-" + job.getId() + "-");
        try {
            List<Path> clips = new ArrayList<>();
            for (BookPage page : pages) {
                Path clip = buildPageClip(dir, job, page);
                if (clip != null) {
                    clips.add(clip);
                }
            }
            if (clips.isEmpty()) {
                throw new IllegalStateException("생성된 페이지 클립이 없음 job=" + job.getId());
            }
            Path finalMp4 = concatClips(dir, clips);
            byte[] bytes = Files.readAllBytes(finalMp4);
            return storage.storeGenerated(job.getId(), "narration.mp4", bytes);
        } finally {
            deleteQuietly(dir);
        }
    }

    // ---------- 페이지 클립 ----------

    /** 한 페이지: 삽화(16:9 패딩) + 낭독 오디오 → mp4 클립. 실패/오디오 없으면 null. */
    private Path buildPageClip(Path dir, VideoJob job, BookPage page) throws Exception {
        byte[] audioWav = buildPageAudio(page, job);
        if (audioWav == null) {
            return null;
        }
        int n = page.getPageNumber();
        Path png = dir.resolve("p" + n + ".png");
        Path wav = dir.resolve("p" + n + ".wav");
        Path mp4 = dir.resolve("p" + n + ".mp4");

        writePng(png, storage.loadByUrl(page.getImageUrl()));
        Files.write(wav, audioWav);

        String vf = "scale=%d:%d:force_original_aspect_ratio=decrease,"
                .formatted(width, height)
                + "pad=%d:%d:(ow-iw)/2:(oh-ih)/2:color=white,format=yuv420p".formatted(width, height);
        run(dir, List.of(
                ffmpeg, "-y",
                "-loop", "1", "-i", png.toString(),
                "-i", wav.toString(),
                "-c:v", "libx264", "-tune", "stillimage", "-pix_fmt", "yuv420p",
                "-vf", vf, "-r", "25",
                // 모든 클립의 오디오 파라미터를 통일(-ar/-ac) → concat copy 시 호환.
                "-c:a", "aac", "-b:a", "192k", "-ar", "44100", "-ac", "1",
                "-shortest", mp4.toString()));
        return mp4;
    }

    /**
     * 페이지의 세그먼트를 각각 TTS로 합성해 하나의 WAV로 이어붙인다(세그먼트 사이 쉼 포함).
     * 서술(narrator)은 부모 목소리가 준비돼 있으면 ElevenLabs로, 아니면 Gemini 기본 내레이터로 읽는다.
     * 등장인물 대사는 항상 캐릭터별 Gemini 목소리를 쓴다.
     */
    private byte[] buildPageAudio(BookPage page, VideoJob job) {
        List<NarrationSegment> segments = segmentsOf(page);
        if (segments.isEmpty()) {
            return null;
        }
        String protagonist = job.getProtagonistDescription();
        String parentVoiceId = job.getParentVoiceId();
        boolean useParent = parentVoiceId != null && !parentVoiceId.isBlank() && elevenLabs.isConfigured();

        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        int rate = 24000;
        for (NarrationSegment seg : segments) {
            if (seg.text() == null || seg.text().isBlank()) {
                continue;
            }
            boolean narrator = "narration".equals(seg.role()) || "narrator".equalsIgnoreCase(seg.voice());

            // 1) 서술 + 부모 목소리 사용 가능 → ElevenLabs
            if (narrator && useParent) {
                try {
                    byte[] raw = elevenLabs.textToSpeechPcm(seg.text(), parentVoiceId);
                    rate = com.storyai.backend.ai.voice.ElevenLabsClient.SAMPLE_RATE;
                    pcm.write(raw);
                    pcm.write(silence(rate, gapMs));
                    continue;
                } catch (Exception e) {
                    log.warn("부모 목소리 낭독 실패(page {}) → 기본 내레이터로 폴백: {}",
                            page.getPageNumber(), e.getMessage());
                }
            }

            // 2) 기본 경로: 정식 Cloud TTS(한도 넉넉) 우선, 실패 시 Gemini 프리뷰 TTS로 폴백.
            try {
                byte[] wav;
                if (cloudTts.isConfigured()) {
                    try {
                        wav = cloudTts.synthesize(seg.text(), seg.voice());
                    } catch (Exception ce) {
                        log.warn("Cloud TTS 실패 → Gemini 폴백(page {}): {}", page.getPageNumber(), ce.getMessage());
                        VoiceStyle vs = casting.resolve(seg.voice(), protagonist);
                        wav = geminiSpeechWithRetry(seg.text(), vs.voiceName(), vs.style(), page.getPageNumber());
                    }
                } else {
                    VoiceStyle vs = casting.resolve(seg.voice(), protagonist);
                    wav = geminiSpeechWithRetry(seg.text(), vs.voiceName(), vs.style(), page.getPageNumber());
                }
                rate = readWavRate(wav);
                pcm.write(wav, 44, wav.length - 44);      // WAV 헤더(44B) 제외한 PCM
                pcm.write(silence(rate, gapMs));           // 세그먼트 사이 쉼
            } catch (Exception e) {
                log.warn("세그먼트 TTS 최종 실패(page {} voice {}): {}", page.getPageNumber(), seg.voice(), e.getMessage());
            }
            // Cloud TTS는 한도가 넉넉해 스로틀 불필요. Gemini 프리뷰 폴백일 때만 간격을 둔다.
            sleepMs(cloudTts.isConfigured() ? 0 : ttsThrottleMs);
        }
        byte[] all = pcm.toByteArray();
        if (all.length == 0) {
            return null;
        }
        return WavAudio.pcm16ToWav(all, rate, 1);
    }

    /** 페이지 낭독 세그먼트. narrationJson 있으면 파싱, 없으면 전체 text를 내레이터 1개로 폴백. */
    private List<NarrationSegment> segmentsOf(BookPage page) {
        String json = page.getNarrationJson();
        if (json != null && !json.isBlank()) {
            try {
                List<NarrationSegment> segs = objectMapper.readValue(json, new TypeReference<>() {
                });
                if (segs != null && !segs.isEmpty()) {
                    return segs;
                }
            } catch (Exception e) {
                log.warn("narrationJson 파싱 실패(page {}): {}", page.getPageNumber(), e.getMessage());
            }
        }
        String text = page.getText();
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return List.of(new NarrationSegment("narration", "", "narrator", text));
    }

    // ---------- 이어붙이기 ----------

    private Path concatClips(Path dir, List<Path> clips) throws Exception {
        // concat demuxer: 같은 코덱/파라미터로 만든 클립들을 재인코딩 없이 이어붙인다.
        StringBuilder list = new StringBuilder();
        for (Path c : clips) {
            list.append("file '").append(c.getFileName().toString()).append("'\n");
        }
        Path listFile = dir.resolve("concat.txt");
        Files.writeString(listFile, list.toString(), StandardCharsets.UTF_8);

        Path finalMp4 = dir.resolve("narration.mp4");
        run(dir, List.of(
                ffmpeg, "-y",
                "-f", "concat", "-safe", "0", "-i", listFile.toString(),
                "-c", "copy", finalMp4.toString()));
        return finalMp4;
    }

    // ---------- 헬퍼 ----------

    /** 삽화 바이트를 PNG로 정규화해 저장. 없거나 깨졌으면 흰 배경 대체. */
    private void writePng(Path png, byte[] imgBytes) throws Exception {
        BufferedImage img = null;
        if (imgBytes != null) {
            try {
                img = ImageIO.read(new ByteArrayInputStream(imgBytes));
            } catch (Exception ignore) {
                // 아래 대체 캔버스로 처리
            }
        }
        if (img == null) {
            img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.dispose();
        }
        ImageIO.write(img, "png", png.toFile());
    }

    /** 저장소 상대 URL("/api/files/...")을 이메일에 넣을 절대 URL로 바꾼다. */
    private String publicUrl(String url) {
        if (url == null || url.isBlank() || url.startsWith("http")) {
            return url;
        }
        String base = apiBaseUrl == null ? "" : apiBaseUrl.replaceAll("/+$", "");
        return base + url;
    }

    /** Gemini TTS를 속도제한(429) 등에 견디게 재시도한다(점점 길게 대기). 모두 실패하면 예외. */
    private byte[] geminiSpeechWithRetry(String text, String voiceName, String style, int pageNum) {
        RuntimeException last = null;
        for (int i = 0; i < TTS_RETRY_WAITS_MS.length; i++) {
            if (TTS_RETRY_WAITS_MS[i] > 0) {
                log.info("TTS 재시도 대기 {}ms (page {}, {}/{})",
                        TTS_RETRY_WAITS_MS[i], pageNum, i + 1, TTS_RETRY_WAITS_MS.length);
                sleepMs(TTS_RETRY_WAITS_MS[i]);
            }
            try {
                return gemini.generateSpeech(text, voiceName, style);
            } catch (RuntimeException e) {
                last = e;
            }
        }
        throw last != null ? last : new IllegalStateException("Gemini TTS 실패");
    }

    private void sleepMs(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private byte[] silence(int rate, int ms) {
        int samples = (int) ((long) rate * ms / 1000);
        return new byte[samples * 2]; // 16-bit mono = 2 bytes/sample, 0 = 무음
    }

    /** WAV 헤더(리틀엔디언)에서 샘플레이트(오프셋 24)를 읽는다. */
    private int readWavRate(byte[] wav) {
        if (wav.length < 28) {
            return 24000;
        }
        return (wav[24] & 0xFF)
                | ((wav[25] & 0xFF) << 8)
                | ((wav[26] & 0xFF) << 16)
                | ((wav[27] & 0xFF) << 24);
    }

    private void run(Path workDir, List<String> cmd) throws Exception {
        Process p = new ProcessBuilder(cmd)
                .directory(workDir.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean done = p.waitFor(10, java.util.concurrent.TimeUnit.MINUTES);
        if (!done) {
            p.destroyForcibly();
            throw new IllegalStateException("ffmpeg 시간 초과: " + String.join(" ", cmd));
        }
        if (p.exitValue() != 0) {
            String tail = output.length() > 1200 ? output.substring(output.length() - 1200) : output;
            throw new IllegalStateException("ffmpeg 실패(exit " + p.exitValue() + "): " + tail);
        }
    }

    private void deleteQuietly(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        } catch (Exception e) {
            log.warn("임시 디렉터리 정리 실패 {}: {}", dir, e.getMessage());
        }
    }
}
