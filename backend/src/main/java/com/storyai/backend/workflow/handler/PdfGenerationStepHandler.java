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

    @Override
    public WorkflowStep getStep() {
        return WorkflowStep.PDF_GENERATION;
    }

    @Override
    public void execute(VideoJob job) {
        List<BookPage> pages = bookPageRepository.findByVideoJobIdOrderByPageNumberAsc(job.getId());
        Font jua, gaegu;
        try {
            jua = loadFont("/fonts/Jua-Regular.ttf");
            gaegu = loadFont("/fonts/Gaegu-Bold.ttf");
        } catch (Exception e) {
            throw new IllegalStateException("폰트 로드 실패: " + e.getMessage(), e);
        }

        try (PDDocument doc = new PDDocument()) {
            String title = job.getGeneratedTitle() != null ? job.getGeneratedTitle() : "이야기책";
            addPage(doc, composeCover(title, coverImage(job), jua, gaegu));
            if (job.getDedication() != null && !job.getDedication().isBlank()) {
                addPage(doc, composeDedication(job.getDedication(), gaegu, jua));
            }
            for (int i = 0; i < pages.size(); i++) {
                addPage(doc, composePage(i, pages.size(), pages.get(i), gaegu, jua));
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            localStorage.write(localStorage.bookPdfPath(job.getId()), baos.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("PDF 생성 실패: " + e.getMessage(), e);
        }

        job.setResultUrl("/api/video-jobs/%d/download".formatted(job.getId()));
    }

    // ---------- 페이지 합성 ----------

    private BufferedImage composeCover(String title, byte[] imgBytes, Font jua, Font gaegu) {
        BufferedImage bmp = canvas();
        Graphics2D g = graphics(bmp);
        scatter(g, 60, 60, W - 120, H - 120, 22, 7);
        drawImageFit(g, image(imgBytes), M, M, 820, H - 2 * M);
        int tx = 820 + M + 80, tw = W - tx - M;
        drawParagraph(g, title, jua.deriveFont(78f), ACCENT, tx, H / 2 - 220, tw, 360);
        drawParagraph(g, BYLINE, gaegu.deriveFont(40f), SUBTLE, tx, H / 2 + 170, tw, 90);
        g.dispose();
        return bmp;
    }

    private BufferedImage composeDedication(String dedication, Font gaegu, Font jua) {
        BufferedImage bmp = canvas();
        Graphics2D g = graphics(bmp);
        scatter(g, 80, 80, W - 160, H - 160, 16, 11);
        // 상단 하트(♥) — Jua 폰트에 글리프 있으면 표시
        drawParagraph(g, "♡", jua.deriveFont(90f), ACCENT, W / 2 - 150, H / 2 - 320, 300, 130);
        drawParagraph(g, dedication, gaegu.deriveFont(58f), INK, W / 2 - 760, H / 2 - 150, 1520, 420);
        g.dispose();
        return bmp;
    }

    private BufferedImage composePage(int i, int total, BookPage page, Font gaegu, Font jua) {
        BufferedImage bmp = canvas();
        Graphics2D g = graphics(bmp);
        String lay = layoutFor(i, total);
        BufferedImage img = image(localStorage.loadByUrl(page.getImageUrl()));
        String text = page.getText() != null ? page.getText() : "";
        Font body = gaegu.deriveFont(56f);
        Font pn = jua.deriveFont(32f);
        int panelW = (W - 2 * M - GAP) / 2;

        switch (lay) {
            case "L" -> {
                drawImageFit(g, img, M, M, panelW, H - 2 * M);
                textPanel(g, M + panelW + GAP, M, panelW, H - 2 * M, text, page.getPageNumber(), body, pn, i + 3);
            }
            case "R" -> {
                drawImageFit(g, img, M + panelW + GAP, M, panelW, H - 2 * M);
                textPanel(g, M, M, panelW, H - 2 * M, text, page.getPageNumber(), body, pn, i + 3);
            }
            case "T" -> {
                int imgH = 800;
                scatter(g, M, M, W - 2 * M, imgH, 10, i + 20);
                drawImageFit(g, img, M, M, W - 2 * M, imgH);
                textPanel(g, M, M + imgH + 30, W - 2 * M, H - 2 * M - imgH - 30, text, page.getPageNumber(), body, pn, i + 3);
            }
            case "FL" -> {
                int iw = drawImageCoverHeight(g, img, 0, 0, H);
                int panX = Math.max(iw + 40, W - 820);
                textPanel(g, panX, (H - 900) / 2, W - panX - 70, 900, text, page.getPageNumber(), body, pn, i + 3);
            }
            case "FR" -> {
                int iw = img != null ? (int) (img.getWidth() * ((double) H / img.getHeight())) : W / 2;
                if (img != null) g.drawImage(img, W - iw, 0, iw, H, null);
                int panW = Math.min(760, (W - iw) - 110);
                if (panW < 620) panW = 620;
                textPanel(g, 70, (H - 900) / 2, panW, 900, text, page.getPageNumber(), body, pn, i + 3);
            }
            default -> { }
        }
        g.dispose();
        return bmp;
    }

    /** 페이지 인덱스에 따라 레이아웃을 정한다(도입·엔딩=풀블리드, 3의 배수=상하, 그 외 좌/우/풀블리드 순환). */
    private String layoutFor(int i, int total) {
        if (i == 0 || i == total - 1) {
            return "FL";
        }
        String[] cycle = {"R", "L", "T", "R", "L", "T", "R", "L", "FR"};
        return cycle[(i - 1) % cycle.length];
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
        List<StoryCharacter> chars = storyCharacterRepository.findByVideoJobIdOrderByIdAsc(job.getId());
        StoryCharacter main = chars.stream().filter(c -> c.getRole() == CharacterRole.MAIN).findFirst()
                .orElse(chars.isEmpty() ? null : chars.get(0));
        if (main == null) {
            return null;
        }
        // 표지는 "본인" 인식을 위해 평상복(실제 옷) 시트를 우선 사용.
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
