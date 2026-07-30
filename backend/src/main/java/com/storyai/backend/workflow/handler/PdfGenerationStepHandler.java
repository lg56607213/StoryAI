package com.storyai.backend.workflow.handler;

import com.storyai.backend.domain.bookpage.BookPage;
import com.storyai.backend.domain.bookpage.BookPageRepository;
import com.storyai.backend.domain.storycharacter.CharacterRole;
import com.storyai.backend.domain.storycharacter.StoryCharacter;
import com.storyai.backend.domain.storycharacter.StoryCharacterRepository;
import com.storyai.backend.domain.videojob.VideoJob;
import com.storyai.backend.domain.videojob.WorkflowStep;
import com.storyai.backend.storage.StorageService;
import com.storyai.backend.workflow.WorkflowStepHandler;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 동화책 PDF 생성 (A4 가로 · 그림 위주 · 손글씨 폰트).
 * 각 페이지를 landscape 이미지로 합성(그림 + 손글씨 텍스트 + 파스텔 장식)한 뒤 PDF로 묶는다.
 * 레이아웃은 페이지마다 좌/우/상하/풀블리드를 섞어 단조롭지 않게 한다.
 */
@Component
@RequiredArgsConstructor
public class PdfGenerationStepHandler implements WorkflowStepHandler {

    // 세로형 책: 내지 재단 사이즈 185×255mm(비율 0.7255). 6px/mm → 표준 152DPI, 실물(2배) 304DPI.
    private static final int W = 1110, H = 1530, M = 100, GAP = 70;
    /** 내지 재단 사이즈(mm) — PDF 페이지(포인트) 계산용. */
    private static final float PAGE_W_MM = 185f, PAGE_H_MM = 255f;
    private static final Color CREAM = new Color(251, 246, 239);
    private static final Color PANEL = new Color(255, 252, 247);
    private static final Color INK = new Color(90, 74, 84);
    private static final Color ACCENT = new Color(225, 116, 155);
    private static final Color SUBTLE = new Color(150, 140, 150);
    private static final Color[] DECO = {
            new Color(244, 198, 216, 70), new Color(240, 220, 150, 60),
            new Color(207, 192, 236, 55), new Color(195, 230, 212, 55)
    };
    private static final String BYLINE = "우리 아이가 주인공인 동화";

    private final BookPageRepository bookPageRepository;
    private final StoryCharacterRepository storyCharacterRepository;
    private final StorageService localStorage;
    private final com.storyai.backend.notify.EmailNotifier emailNotifier;

    @org.springframework.beans.factory.annotation.Value("${storyai.book.preview-pages:4}")
    private int previewPages;

    /** 실물책(인쇄) 주문일 때 페이지를 이 배수만큼 고해상도로 렌더링한다(글자·레이아웃이 또렷해짐). */
    @org.springframework.beans.factory.annotation.Value("${storyai.book.hires-scale:2}")
    private int hiresScale;

    // ---- A3 하드커버 표지(싸바리) 치수(mm) : [감쌈여백 | 뒤표지 | 책등 | 앞표지 | 감쌈여백] × 높이 ----
    /** 감쌈(turn-in) 여백 — 보드 안쪽으로 접히는 부분. */
    @org.springframework.beans.factory.annotation.Value("${storyai.book.cover.turn-in-mm:15}")
    private float coverTurnInMm;
    /** 앞/뒤 표지 판 가로(보드 오버행 포함, 내지 185 + 3). */
    @org.springframework.beans.factory.annotation.Value("${storyai.book.cover.panel-w-mm:188}")
    private float coverPanelWMm;
    /** 앞/뒤 표지 판 세로(내지 255 + 3). */
    @org.springframework.beans.factory.annotation.Value("${storyai.book.cover.panel-h-mm:258}")
    private float coverPanelHMm;
    /** 책등 두께(mm). 0이면 페이지 수로 자동 추정. 실제 책 두께를 재서 넣는 것이 가장 정확. */
    @org.springframework.beans.factory.annotation.Value("${storyai.book.cover.spine-mm:0}")
    private float coverSpineMm;

    /** 현재 페이지 렌더 배수(스레드별). 실물책이면 hiresScale, 아니면 1. 동시 생성 안전을 위해 ThreadLocal. */
    private final ThreadLocal<Integer> pageScale = ThreadLocal.withInitial(() -> 1);

    /** 인쇄 재단여백(mm). 실물책 주문 시 사방으로 이만큼 그림을 더 채워 재단 오차에도 흰 여백이 안 보이게. */
    private static final int BLEED_MM = 5;
    /** 현재 렌더의 재단여백(px, 트림 기준 좌표계). 실물책이면 30px(=5mm), 아니면 0. */
    private final ThreadLocal<Integer> bleedPx = ThreadLocal.withInitial(() -> 0);

    private int b() {
        return bleedPx.get();
    }

    /** 재단여백 포함 캔버스 가로/세로(트림 좌표계, pageScale 적용 전). */
    private int cw() {
        return W + 2 * b();
    }

    private int ch() {
        return H + 2 * b();
    }

    @Override
    public WorkflowStep getStep() {
        return WorkflowStep.PDF_GENERATION;
    }

    @Override
    public void execute(VideoJob job) {
        // 실물책 주문이면 고해상도 + 재단여백(5mm)으로 렌더(인쇄용). PDF만 주문·미리보기는 기본 화질·여백 없음.
        boolean physical = job.isPhysicalBookRequested();
        pageScale.set(physical ? Math.max(1, hiresScale) : 1);
        bleedPx.set(physical ? Math.round(BLEED_MM * (float) W / PAGE_W_MM) : 0); // 5mm≈30px
        try {
        List<BookPage> pages = bookPageRepository.findByVideoJobIdOrderByPageNumberAsc(job.getId());
        // 미리보기 단계면 앞쪽 previewPages 페이지만 PDF에 담는다.
        boolean preview = job.getBookPhase() == com.storyai.backend.domain.videojob.BookPhase.PREVIEW;
        if (preview && pages.size() > previewPages) {
            pages = pages.subList(0, previewPages);
        }
        Font jua, gaegu;
        try {
            jua = loadFont("/fonts/Jua-Regular.ttf");
            gaegu = loadFont("/fonts/Gaegu-Bold.ttf");
        } catch (Exception e) {
            throw new IllegalStateException("폰트 로드 실패: " + e.getMessage(), e);
        }

        byte[] pdfBytes;
        try (PDDocument doc = new PDDocument()) {
            String title = job.getGeneratedTitle() != null ? job.getGeneratedTitle() : "이야기책";
            addPage(doc, composeCover(title, coverImage(job), jua, gaegu));
            byte[] dedicationPhoto = localStorage.loadByUrl(job.getDedicationPhotoUrl());
            boolean hasDedText = job.getDedication() != null && !job.getDedication().isBlank();
            if (hasDedText || dedicationPhoto != null) {
                addPage(doc, composeDedication(job.getDedication(), dedicationPhoto, gaegu, jua));
            }
            for (int i = 0; i < pages.size(); i++) {
                addPage(doc, composePage(i, pages.size(), pages.get(i), gaegu, jua));
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            pdfBytes = baos.toByteArray();
            localStorage.storeBookPdf(job.getId(), pdfBytes);
        } catch (Exception e) {
            throw new IllegalStateException("PDF 생성 실패: " + e.getMessage(), e);
        }

        job.setResultUrl("/api/video-jobs/%d/download".formatted(job.getId()));

        // 전체 생성이 끝나면 완성본 PDF를 이메일로 발송(수단 미설정 시 로그만). 미리보기 단계에선 보내지 않음.
        if (!preview) {
            boolean sent = emailNotifier.sendBookReady(job.getDeliveryEmail(),
                    job.getGeneratedTitle() != null ? job.getGeneratedTitle() : "동화책", pdfBytes, job.getResultUrl());
            job.setEmailSent(sent);
        }
        } finally {
            pageScale.remove();
            bleedPx.remove();
        }
    }

    // ---------- 페이지 합성 ----------

    private BufferedImage composeCover(String title, byte[] imgBytes, Font jua, Font gaegu) {
        BufferedImage bmp = canvas();
        Graphics2D g = graphics(bmp);
        // 표지 = 장면 풀블리드 + 하단 제목 밴드 (서점 책 표지 느낌).
        drawImageFullBleed(g, image(imgBytes), cw(), ch());
        g.translate(b(), b()); // (0,0)=트림
        int bandM = 70, bandH = 360, by = H - bandH - 70;
        g.setColor(new Color(0, 0, 0, 30));
        g.fill(new RoundRectangle2D.Float(bandM + 5, by + 8, W - 2 * bandM, bandH, 56, 56));
        g.setColor(new Color(255, 252, 247, 236));
        g.fill(new RoundRectangle2D.Float(bandM, by, W - 2 * bandM, bandH, 56, 56));
        drawParagraph(g, title, jua.deriveFont(60f), ACCENT, bandM + 55, by + 50, W - 2 * bandM - 110, 190);
        drawParagraph(g, BYLINE, gaegu.deriveFont(28f), SUBTLE, bandM + 55, by + bandH - 90, W - 2 * bandM - 110, 60);
        g.translate(-b(), -b());
        drawCropMarks(g);
        g.dispose();
        return bmp;
    }

    private BufferedImage composeDedication(String dedication, byte[] photoBytes, Font gaegu, Font jua) {
        BufferedImage bmp = canvas();
        Graphics2D g = graphics(bmp);
        g.translate(b(), b()); // (0,0)=트림. 배경은 크림이라 재단여백도 크림(흰 여백 문제 없음).
        scatter(g, 60, 60, W - 120, H - 120, 12, 11);
        BufferedImage photo = image(photoBytes);
        String msg = (dedication == null || dedication.isBlank()) ? "" : dedication;
        if (photo != null) {
            // 세로형: 위=흰 액자 사진(원본 그대로), 아래=하트+헌정 메시지.
            int fw = 720, fh = 880, fx = (W - fw) / 2, fy = 130;
            drawPhotoFrame(g, photo, fx, fy, fw, fh);
            int ty = fy + fh + 60;
            drawParagraph(g, "♡", jua.deriveFont(64f), ACCENT, W / 2 - 100, ty, 200, 90);
            if (!msg.isEmpty()) {
                drawParagraph(g, msg, gaegu.deriveFont(42f), INK, 120, ty + 110, W - 240, H - ty - 200);
            }
        } else {
            // 사진 없으면 하트+메시지 중앙 배치.
            drawParagraph(g, "♡", jua.deriveFont(72f), ACCENT, W / 2 - 110, H / 2 - 260, 220, 110);
            drawParagraph(g, msg, gaegu.deriveFont(46f), INK, 120, H / 2 - 110, W - 240, 360);
        }
        g.translate(-b(), -b());
        drawCropMarks(g);
        g.dispose();
        return bmp;
    }

    /** 흰 매트 액자에 사진을 원본 비율 그대로(fit) 넣는다. AI 변환 없이 업로드 사진을 그대로 표시. */
    private void drawPhotoFrame(Graphics2D g, BufferedImage photo, int x, int y, int w, int h) {
        g.setColor(new Color(60, 50, 55, 30)); // 그림자
        g.fill(new RoundRectangle2D.Float(x + 12, y + 16, w, h, 36, 36));
        g.setColor(Color.WHITE);             // 흰 액자
        g.fill(new RoundRectangle2D.Float(x, y, w, h, 36, 36));
        int pad = 34;                         // 사진 전체가 보이도록 비율 유지(fit)
        drawImageFit(g, photo, x + pad, y + pad, w - 2 * pad, h - 2 * pad);
    }

    private BufferedImage composePage(int i, int total, BookPage page, Font gaegu, Font jua) {
        BufferedImage bmp = canvas();
        Graphics2D g = graphics(bmp);
        BufferedImage img = image(localStorage.loadByUrl(page.getImageUrl()));
        String text = page.getText() != null ? page.getText() : "";
        Font body = gaegu.deriveFont(46f);
        Font pn = jua.deriveFont(24f);

        // 그림을 재단여백 포함 전체에 꽉 채우고(풀블리드), 글은 트림(재단) 영역 기준으로 얹는다.
        drawImageFullBleed(g, img, cw(), ch());
        g.translate(b(), b()); // 이후 좌표(0,0)=재단선 안쪽(트림)
        if (!text.isBlank()) {
            // 페이지마다 아래/위 번갈아 배치해 단조롭지 않게(틀에 갇힌 느낌 제거).
            boolean bottom = (i % 2 == 0);
            drawStoryText(g, text, page.getPageNumber(), bottom, body, pn);
        }
        g.translate(-b(), -b());
        drawCropMarks(g);
        g.dispose();
        return bmp;
    }

    /**
     * 이야기 문구를 "박스(말풍선/밴드) 없이" 그림 위에 자연스럽게 얹는다.
     * 어떤 배경에서도 잘 읽히도록 은은한 그라데이션 스크림 + 흰 글씨 + 부드러운 그림자를 쓴다.
     */
    private void drawStoryText(Graphics2D g, String text, int pageNum, boolean bottom, Font body, Font pn) {
        int margin = 90; // 좌우 안전여백(≈15mm) — 제본쪽 글자 잘림 방지
        int maxW = W - 2 * margin;
        g.setFont(body);
        FontMetrics fm = g.getFontMetrics();
        List<String> lines = wrap(fm, text, maxW);
        int lineH = (int) (fm.getHeight() * 1.22);
        int totalH = lines.size() * lineH;
        int padV = 70;
        int areaH = totalH + padV * 2;
        int areaY = bottom ? H - areaH : 0;

        // 그라데이션 스크림(박스 아님, 자연스러운 음영)을 충분히 진하게 → 흰 글씨가 확실히 보이게.
        int scrimH = areaH + 150;
        int scrimY = bottom ? H - scrimH : 0;
        Paint oldPaint = g.getPaint();
        Color clear = new Color(15, 12, 18, 0);
        Color dark = new Color(15, 12, 18, 210);
        g.setPaint(bottom
                ? new GradientPaint(0, scrimY, clear, 0, scrimY + scrimH, dark)
                : new GradientPaint(0, scrimY, dark, 0, scrimY + scrimH, clear));
        g.fillRect(0, scrimY, W, scrimH);
        g.setPaint(oldPaint);

        // 흰 글씨 + 진한 외곽선(8방향) → 밝은 배경 위에서도 또렷하게 읽힘.
        int startY = areaY + padV + fm.getAscent();
        Color outline = new Color(0, 0, 0, 230);
        int t = 3; // 외곽선 두께
        for (int k = 0; k < lines.size(); k++) {
            String line = lines.get(k);
            int lw = fm.stringWidth(line);
            int lx = (W - lw) / 2;
            int ly = startY + k * lineH;
            g.setColor(outline);
            for (int dx = -t; dx <= t; dx++) {
                for (int dy = -t; dy <= t; dy++) {
                    if (dx != 0 || dy != 0) {
                        g.drawString(line, lx + dx, ly + dy);
                    }
                }
            }
            g.setColor(Color.WHITE);
            g.drawString(line, lx, ly);
        }

        // 페이지 번호(작게, 흐릿한 흰색).
        if (pageNum > 0) {
            g.setFont(pn);
            g.setColor(new Color(255, 255, 255, 200));
            FontMetrics pfm = g.getFontMetrics();
            String p = String.valueOf(pageNum);
            int py = bottom ? H - 34 : scrimY + scrimH + 44;
            g.drawString(p, (W - pfm.stringWidth(p)) / 2, py);
        }
    }

    /** 그림을 페이지(또는 박스) 전체에 여백 없이 꽉 채운다(cover, 둥근 모서리 없음). */
    private void drawImageFullBleed(Graphics2D g, BufferedImage img, int w, int h) {
        if (img == null) {
            placeholder(g, 0, 0, w, h);
            return;
        }
        double s = Math.max((double) w / img.getWidth(), (double) h / img.getHeight());
        int iw = (int) (img.getWidth() * s), ih = (int) (img.getHeight() * s);
        g.drawImage(img, (w - iw) / 2, (h - ih) / 2, iw, ih, null);
    }

    /** 재단선(crop marks): 트림 네 모서리에 L자 표식을 재단여백 안에 그린다(실물책만). */
    private void drawCropMarks(Graphics2D g) {
        int bl = b();
        if (bl <= 0) {
            return;
        }
        int x0 = bl, y0 = bl, x1 = bl + W, y1 = bl + H; // 트림(재단) 사각형 모서리(캔버스 좌표)
        int len = bl;         // 표식 길이 = 재단여백
        int gap = Math.max(2, bl / 6); // 트림선에서 살짝 띄움
        // 배경이 어두워도 보이게 흰색(굵게)→검정(가늘게) 두 번 그린다.
        drawCropStroke(g, x0, y0, x1, y1, len, gap, new Color(255, 255, 255, 235), Math.max(2f, bl * 0.24f));
        drawCropStroke(g, x0, y0, x1, y1, len, gap, Color.BLACK, Math.max(1f, bl * 0.10f));
    }

    private void drawCropStroke(Graphics2D g, int x0, int y0, int x1, int y1,
                                int len, int gap, Color c, float w) {
        g.setColor(c);
        g.setStroke(new BasicStroke(w, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        // 좌상
        g.drawLine(x0 - len, y0, x0 - gap, y0);
        g.drawLine(x0, y0 - len, x0, y0 - gap);
        // 우상
        g.drawLine(x1 + gap, y0, x1 + len, y0);
        g.drawLine(x1, y0 - len, x1, y0 - gap);
        // 좌하
        g.drawLine(x0 - len, y1, x0 - gap, y1);
        g.drawLine(x0, y1 + gap, x0, y1 + len);
        // 우하
        g.drawLine(x1 + gap, y1, x1 + len, y1);
        g.drawLine(x1, y1 + gap, x1, y1 + len);
        g.setStroke(new BasicStroke(1f));
    }

    // ---------- 그리기 헬퍼 ----------

    private BufferedImage canvas() {
        int s = Math.max(1, pageScale.get());
        // 재단여백 포함 크기로 캔버스 생성(실물책이면 사방 5mm 더 큼).
        BufferedImage bi = new BufferedImage(cw() * s, ch() * s, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bi.createGraphics();
        g.setColor(CREAM);
        g.fillRect(0, 0, cw() * s, ch() * s);
        g.dispose();
        return bi;
    }

    private Graphics2D graphics(BufferedImage b) {
        Graphics2D g = b.createGraphics();
        // 고해상도 배수만큼 좌표계를 확대 → 이후 모든 그리기(글자·도형)는 원래 좌표 그대로,
        // 실제로는 고해상도로 래스터화된다(글자가 또렷해짐). 삽화는 아래 보간 힌트로 고품질 확대.
        int s = Math.max(1, pageScale.get());
        if (s != 1) {
            g.scale(s, s);
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }

    private void textPanel(Graphics2D g, int x, int y, int w, int h, String text, int pageNum,
                           Font body, Font pn, int seed) {
        g.setColor(PANEL);
        g.fill(new RoundRectangle2D.Float(x, y, w, h, 80, 80));
        scatter(g, x + 24, y + 24, w - 48, h - 48, 5, seed);
        int pad = 64;
        drawParagraph(g, text, body, INK, x + pad, y + pad, w - 2 * pad, h - 2 * pad - 46);
        if (pageNum > 0) {
            drawParagraph(g, String.valueOf(pageNum), pn, ACCENT, x, y + h - 70, w, 50);
        }
    }

    /** 사각형 안에 자동 줄바꿈 + 가운데 정렬로 텍스트를 그린다. */
    private void drawParagraph(Graphics2D g, String text, Font font, Color color, int rx, int ry, int rw, int rh) {
        g.setFont(font);
        g.setColor(color);
        FontMetrics fm = g.getFontMetrics();
        List<String> lines = wrap(fm, text, rw);
        int lineH = (int) (fm.getHeight() * 1.15);
        int totalH = lines.size() * lineH;
        int startY = ry + Math.max(0, (rh - totalH) / 2) + fm.getAscent();
        for (int k = 0; k < lines.size(); k++) {
            String line = lines.get(k);
            int lw = fm.stringWidth(line);
            g.drawString(line, rx + (rw - lw) / 2, startY + k * lineH);
        }
    }

    private List<String> wrap(FontMetrics fm, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        for (String para : text.replace("\r", "").split("\n", -1)) {
            StringBuilder cur = new StringBuilder();
            for (int i = 0; i < para.length(); i++) {
                char c = para.charAt(i);
                if (fm.stringWidth(cur.toString() + c) > maxWidth && cur.length() > 0) {
                    lines.add(cur.toString());
                    cur = new StringBuilder().append(c);
                } else {
                    cur.append(c);
                }
            }
            lines.add(cur.toString());
        }
        return lines;
    }

    private void scatter(Graphics2D g, int x, int y, int w, int h, int count, int seed) {
        Random r = new Random(seed);
        for (int k = 0; k < count; k++) {
            int rad = 14 + r.nextInt(32);
            int px = x + r.nextInt(Math.max(1, w - rad));
            int py = y + r.nextInt(Math.max(1, h - rad));
            g.setColor(DECO[r.nextInt(DECO.length)]);
            g.fillOval(px, py, rad, rad);
        }
    }

    private void drawImageFit(Graphics2D g, BufferedImage img, int x, int y, int bw, int bh) {
        if (img == null) {
            placeholder(g, x, y, bw, bh);
            return;
        }
        double s = Math.min((double) bw / img.getWidth(), (double) bh / img.getHeight());
        int w = (int) (img.getWidth() * s), h = (int) (img.getHeight() * s);
        g.drawImage(img, x + (bw - w) / 2, y + (bh - h) / 2, w, h, null);
    }

    /** 박스를 꽉 채우도록(cover) 확대 후, 넘치는 부분만 잘라낸다. 여백 없이 그림이 가득 찬다. */
    private void drawImageCover(Graphics2D g, BufferedImage img, int x, int y, int bw, int bh) {
        if (img == null) {
            placeholder(g, x, y, bw, bh);
            return;
        }
        double s = Math.max((double) bw / img.getWidth(), (double) bh / img.getHeight());
        int w = (int) (img.getWidth() * s), h = (int) (img.getHeight() * s);
        java.awt.Shape oldClip = g.getClip();
        g.setClip(new RoundRectangle2D.Float(x, y, bw, bh, 60, 60));
        g.drawImage(img, x + (bw - w) / 2, y + (bh - h) / 2, w, h, null);
        g.setClip(oldClip);
    }

    private int drawImageCoverHeight(Graphics2D g, BufferedImage img, int x, int y, int bh) {
        if (img == null) {
            placeholder(g, x, y, W / 2, bh);
            return W / 2;
        }
        double s = (double) bh / img.getHeight();
        int w = (int) (img.getWidth() * s);
        g.drawImage(img, x, y, w, bh, null);
        return w;
    }

    private void placeholder(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(241, 231, 218));
        g.fill(new RoundRectangle2D.Float(x, y, w, h, 40, 40));
    }

    private BufferedImage image(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] coverImage(VideoJob job) {
        // 표지 배경 = 첫 삽화(장면). 초반은 실제 옷 차림이라 "본인" 인식에도 좋고 장면이라 표지가 풍성함.
        for (BookPage p : bookPageRepository.findByVideoJobIdOrderByPageNumberAsc(job.getId())) {
            byte[] scene = localStorage.loadByUrl(p.getImageUrl());
            if (scene != null) {
                return scene;
            }
        }
        // 폴백: 주인공 평상복(실제 옷) 시트.
        List<StoryCharacter> chars = storyCharacterRepository.findByVideoJobIdOrderByIdAsc(job.getId());
        StoryCharacter main = chars.stream().filter(c -> c.getRole() == CharacterRole.MAIN).findFirst()
                .orElse(chars.isEmpty() ? null : chars.get(0));
        if (main == null) {
            return null;
        }
        byte[] everyday = localStorage.loadByUrl(main.getEverydaySheetUrl());
        return everyday != null ? everyday : localStorage.loadByUrl(main.getCharacterSheetUrl());
    }

    // ================= A3 하드커버 감쌈(싸바리) 표지 =================

    /**
     * A3(420×297mm) 하드커버 감쌈 표지 PDF를 즉석 생성한다(관리자 인쇄용).
     * 가로: [감쌈여백 | 뒤표지 | 책등 | 앞표지 | 감쌈여백], 세로: [감쌈여백 | 표지판 | 감쌈여백].
     * 앞표지=삽화 풀블리드+제목, 책등=단색 띠, 뒤표지=로고·바이라인. 가장자리에 접기/재단 표식.
     */
    public byte[] generateCoverPdf(VideoJob job) throws Exception {
        Font jua = loadFont("/fonts/Jua-Regular.ttf");
        Font gaegu = loadFont("/fonts/Gaegu-Bold.ttf");

        final int ppm = 8;               // px/mm (~203 DPI)
        final int A3W = 420, A3H = 297;  // A3 가로(mm)
        int cW = A3W * ppm, cH = A3H * ppm;

        float spineMm = coverSpineMm > 0 ? coverSpineMm : estimateSpineMm(job);
        int TI = Math.round(coverTurnInMm * ppm);
        int PW = Math.round(coverPanelWMm * ppm);
        int PH = Math.round(coverPanelHMm * ppm);
        int SP = Math.round(spineMm * ppm);
        int block = TI + PW + SP + PW + TI;
        int offX = Math.max(0, (cW - block) / 2);
        int offY = Math.max(0, (cH - (TI + PH + TI)) / 2);
        int backX = offX + TI;
        int spineX = backX + PW;
        int frontX = spineX + SP;
        int panelY = offY + TI;

        BufferedImage bmp = new BufferedImage(cW, cH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bmp.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // 배경: 크림 전체 채움(감쌈여백·뒤표지에 흰색이 안 나오게).
        g.setColor(CREAM);
        g.fillRect(0, 0, cW, cH);

        // 앞표지: 삽화를 책등 접이선~오른쪽/위/아래 A3 끝까지 풀블리드(펼침 시 흰 여백 방지).
        BufferedImage cover = image(coverImage(job));
        drawCoverInto(g, cover, frontX, 0, cW - frontX, cH);
        // 앞표지 하단 제목 밴드
        String title = job.getGeneratedTitle() != null ? job.getGeneratedTitle() : "이야기책";
        int bandM = 14 * ppm, bandH = 44 * ppm, by = panelY + PH - bandH - 16 * ppm;
        int bx = frontX + bandM, bw = PW - 2 * bandM;
        g.setColor(new Color(0, 0, 0, 28));
        g.fill(new RoundRectangle2D.Float(bx + 5, by + 7, bw, bandH, 40, 40));
        g.setColor(new Color(255, 252, 247, 238));
        g.fill(new RoundRectangle2D.Float(bx, by, bw, bandH, 40, 40));
        drawParagraph(g, title, jua.deriveFont(58f), ACCENT, bx + 44, by + 40, bw - 88, bandH - 130);
        drawParagraph(g, BYLINE, gaegu.deriveFont(26f), SUBTLE, bx + 44, by + bandH - 74, bw - 88, 54);

        // 책등: 단색 띠(액센트). 12mm 이상이면 세로 제목.
        g.setColor(ACCENT);
        g.fillRect(spineX, 0, SP, cH);
        if (spineMm >= 12) {
            drawSpineTitle(g, title, jua, spineX, SP, panelY, PH);
        }

        // 뒤표지: 로고/바이라인.
        drawParagraph(g, "♡", jua.deriveFont(72f), ACCENT, backX + PW / 2 - 100, panelY + PH / 2 - 210, 200, 110);
        drawParagraph(g, BYLINE, gaegu.deriveFont(34f), INK, backX + 60, panelY + PH / 2 - 70, PW - 120, 130);
        drawParagraph(g, "todayhero.co.kr", gaegu.deriveFont(24f), SUBTLE, backX + 60, panelY + PH - 130, PW - 120, 60);

        // 접기/재단 표식(A3 가장자리, 감쌈여백 안 → 접으면 안 보임).
        g.setColor(new Color(110, 110, 110));
        g.setStroke(new BasicStroke(2f));
        int t = 3 * ppm;
        for (int xv : new int[]{backX, spineX, frontX, frontX + PW}) { // 수직 접이선
            g.drawLine(xv, 0, xv, t);
            g.drawLine(xv, cH - t, xv, cH);
        }
        for (int yv : new int[]{panelY, panelY + PH}) {                // 수평 접이선
            g.drawLine(0, yv, t, yv);
            g.drawLine(cW - t, yv, cW, yv);
        }
        g.setStroke(new BasicStroke(1f));
        g.dispose();

        try (PDDocument doc = new PDDocument()) {
            PDImageXObject xo = JPEGFactory.createFromImage(doc, bmp, 0.95f);
            float ptW = A3W * 72f / 25.4f, ptH = A3H * 72f / 25.4f;
            PDPage p = new PDPage(new PDRectangle(ptW, ptH));
            doc.addPage(p);
            try (PDPageContentStream cs = new PDPageContentStream(doc, p)) {
                cs.drawImage(xo, 0, 0, ptW, ptH);
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    /** 책 두께(책등) 추정: 보드 2장(≈4mm) + 내지 두께(장수/2 × 0.25mm). */
    private float estimateSpineMm(VideoJob job) {
        int pages = job.getBookPages() != null ? job.getBookPages() : 24;
        return Math.max(6f, 4f + (pages / 2f) * 0.25f);
    }

    /** 이미지를 지정 사각형에 cover-fit(비율 유지, 꽉 채움, 넘치면 클립)로 그린다. */
    private void drawCoverInto(Graphics2D g, BufferedImage img, int x, int y, int w, int h) {
        if (img == null) {
            g.setColor(CREAM);
            g.fillRect(x, y, w, h);
            return;
        }
        double s = Math.max((double) w / img.getWidth(), (double) h / img.getHeight());
        int iw = (int) (img.getWidth() * s), ih = (int) (img.getHeight() * s);
        java.awt.Shape old = g.getClip();
        g.setClip(x, y, w, h);
        g.drawImage(img, x + (w - iw) / 2, y + (h - ih) / 2, iw, ih, null);
        g.setClip(old);
    }

    /** 책등 세로 제목(위→아래). */
    private void drawSpineTitle(Graphics2D g, String title, Font jua, int spineX, int SP, int panelY, int PH) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(Color.WHITE);
        g2.setFont(jua.deriveFont(Math.min(SP * 0.5f, 40f)));
        g2.translate(spineX + SP / 2.0, panelY + PH / 2.0);
        g2.rotate(Math.toRadians(90));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(title, -fm.stringWidth(title) / 2, (fm.getAscent() - fm.getDescent()) / 2);
        g2.dispose();
    }

    private Font loadFont(String resource) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("폰트 리소스 없음: " + resource);
            }
            return Font.createFont(Font.TRUETYPE_FONT, in);
        }
    }

    private void addPage(PDDocument doc, BufferedImage page) throws Exception {
        // 실물책(고해상도)이면 압축을 줄여 인쇄 품질 확보, 일반 PDF는 파일 크기를 위해 0.85.
        float quality = Math.max(1, pageScale.get()) > 1 ? 0.95f : 0.85f;
        PDImageXObject xo = JPEGFactory.createFromImage(doc, page, quality);
        // PDF 페이지 = 재단여백 포함 크기(실물책이면 195×265mm, 아니면 트림 185×255mm). 1mm=72/25.4pt.
        float bleedMm = b() > 0 ? BLEED_MM : 0f;
        float ptW = (PAGE_W_MM + 2 * bleedMm) * 72f / 25.4f, ptH = (PAGE_H_MM + 2 * bleedMm) * 72f / 25.4f;
        PDPage p = new PDPage(new PDRectangle(ptW, ptH));
        doc.addPage(p);
        try (PDPageContentStream cs = new PDPageContentStream(doc, p)) {
            cs.drawImage(xo, 0, 0, ptW, ptH);
        }
    }
}
