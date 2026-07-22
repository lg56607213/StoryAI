package com.storyai.backend.workflow.handler;

import com.storyai.backend.domain.bookpage.BookPage;
import com.storyai.backend.domain.bookpage.BookPageRepository;
import com.storyai.backend.domain.storycharacter.CharacterRole;
import com.storyai.backend.domain.storycharacter.StoryCharacter;
import com.storyai.backend.domain.storycharacter.StoryCharacterRepository;
import com.storyai.backend.domain.videojob.VideoJob;
import com.storyai.backend.domain.videojob.WorkflowStep;
import com.storyai.backend.storage.LocalStorage;
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
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
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

    private static final int W = 2100, H = 1485, M = 100, GAP = 70;
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
    private final LocalStorage localStorage;
    private final com.storyai.backend.notify.EmailNotifier emailNotifier;

    @org.springframework.beans.factory.annotation.Value("${storyai.book.preview-pages:4}")
    private int previewPages;

    @Override
    public WorkflowStep getStep() {
        return WorkflowStep.PDF_GENERATION;
    }

    @Override
    public void execute(VideoJob job) {
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
            localStorage.write(localStorage.bookPdfPath(job.getId()), pdfBytes);
        } catch (Exception e) {
            throw new IllegalStateException("PDF 생성 실패: " + e.getMessage(), e);
        }

        job.setResultUrl("/api/video-jobs/%d/download".formatted(job.getId()));

        // 전체 생성이 끝나면 완성본 PDF를 이메일로 발송(SMTP 미설정 시 로그만). 미리보기 단계에선 보내지 않음.
        if (!preview) {
            emailNotifier.sendBookReady(job.getDeliveryEmail(),
                    job.getGeneratedTitle() != null ? job.getGeneratedTitle() : "동화책", pdfBytes, job.getResultUrl());
        }
    }

    // ---------- 페이지 합성 ----------

    private BufferedImage composeCover(String title, byte[] imgBytes, Font jua, Font gaegu) {
        BufferedImage bmp = canvas();
        Graphics2D g = graphics(bmp);
        // 표지 = 장면 풀블리드 + 하단 제목 밴드 (서점 책 표지 느낌).
        drawImageFullBleed(g, image(imgBytes), W, H);
        int bandM = 90, bandH = 430, by = H - bandH - 90;
        g.setColor(new Color(0, 0, 0, 30));
        g.fill(new RoundRectangle2D.Float(bandM + 6, by + 10, W - 2 * bandM, bandH, 70, 70));
        g.setColor(new Color(255, 252, 247, 236));
        g.fill(new RoundRectangle2D.Float(bandM, by, W - 2 * bandM, bandH, 70, 70));
        drawParagraph(g, title, jua.deriveFont(92f), ACCENT, bandM + 80, by + 66, W - 2 * bandM - 160, 236);
        drawParagraph(g, BYLINE, gaegu.deriveFont(42f), SUBTLE, bandM + 80, by + bandH - 128, W - 2 * bandM - 160, 88);
        g.dispose();
        return bmp;
    }

    private BufferedImage composeDedication(String dedication, byte[] photoBytes, Font gaegu, Font jua) {
        BufferedImage bmp = canvas();
        Graphics2D g = graphics(bmp);
        scatter(g, 80, 80, W - 160, H - 160, 14, 11);
        BufferedImage photo = image(photoBytes);
        String msg = (dedication == null || dedication.isBlank()) ? "" : dedication;
        if (photo != null) {
            // 왼쪽: 흰 액자에 실제 가족 사진(AI 변환 없이 원본 그대로).
            int fw = 900, fh = 1060, fx = 175, fy = (H - fh) / 2;
            drawPhotoFrame(g, photo, fx, fy, fw, fh);
            // 오른쪽: 하트 + 헌정 메시지.
            int tx = fx + fw + 110, tw = W - tx - 150;
            drawParagraph(g, "♡", jua.deriveFont(84f), ACCENT, tx, H / 2 - 340, tw, 120);
            if (!msg.isEmpty()) {
                drawParagraph(g, msg, gaegu.deriveFont(54f), INK, tx, H / 2 - 170, tw, 460);
            }
        } else {
            // 사진 없으면 기존처럼 하트+메시지 중앙 배치.
            drawParagraph(g, "♡", jua.deriveFont(90f), ACCENT, W / 2 - 150, H / 2 - 320, 300, 130);
            drawParagraph(g, msg, gaegu.deriveFont(58f), INK, W / 2 - 760, H / 2 - 150, 1520, 420);
        }
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
        Font body = gaegu.deriveFont(60f);
        Font pn = jua.deriveFont(30f);

        // 그림을 페이지 전체에 꽉 채우고(풀블리드), 글은 반투명 밴드에 얹는다 → 빈 여백 제거.
        drawImageFullBleed(g, img, W, H);
        if (!text.isBlank()) {
            int bandM = 90, bandH = 340;
            // 페이지마다 밴드를 아래/위 번갈아 배치해 단조롭지 않게.
            boolean bottom = (i % 2 == 0);
            int by = bottom ? H - bandH - 80 : 80;
            drawCaptionBand(g, bandM, by, W - 2 * bandM, bandH, text, page.getPageNumber(), body, pn);
        }
        g.dispose();
        return bmp;
    }

    /** 반투명 흰 밴드에 이야기 문구를 얹는다(그림 위 캡션). 빈 패널 없이 페이지가 꽉 찬다. */
    private void drawCaptionBand(Graphics2D g, int x, int y, int w, int h, String text, int pageNum,
                                 Font body, Font pn) {
        g.setColor(new Color(0, 0, 0, 26));
        g.fill(new RoundRectangle2D.Float(x + 6, y + 8, w, h, 64, 64));
        g.setColor(new Color(255, 252, 247, 233));
        g.fill(new RoundRectangle2D.Float(x, y, w, h, 64, 64));
        int pad = 72;
        drawParagraph(g, text, body, INK, x + pad, y + pad, w - 2 * pad, h - 2 * pad - 40);
        if (pageNum > 0) {
            drawParagraph(g, String.valueOf(pageNum), pn, ACCENT, x, y + h - 60, w, 44);
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

    // ---------- 그리기 헬퍼 ----------

    private BufferedImage canvas() {
        BufferedImage b = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = b.createGraphics();
        g.setColor(CREAM);
        g.fillRect(0, 0, W, H);
        g.dispose();
        return b;
    }

    private Graphics2D graphics(BufferedImage b) {
        Graphics2D g = b.createGraphics();
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

    private Font loadFont(String resource) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("폰트 리소스 없음: " + resource);
            }
            return Font.createFont(Font.TRUETYPE_FONT, in);
        }
    }

    private void addPage(PDDocument doc, BufferedImage page) throws Exception {
        PDImageXObject xo = JPEGFactory.createFromImage(doc, page, 0.85f);
        PDPage p = new PDPage(new PDRectangle(842, 595)); // A4 가로
        doc.addPage(p);
        try (PDPageContentStream cs = new PDPageContentStream(doc, p)) {
            cs.drawImage(xo, 0, 0, 842, 595);
        }
    }
}
