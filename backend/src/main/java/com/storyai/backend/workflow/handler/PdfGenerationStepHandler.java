package com.storyai.backend.workflow.handler;

import com.storyai.backend.domain.bookpage.BookPage;
import com.storyai.backend.domain.bookpage.BookPageRepository;
import com.storyai.backend.domain.videojob.VideoJob;
import com.storyai.backend.domain.videojob.WorkflowStep;
import com.storyai.backend.storage.LocalStorage;
import com.storyai.backend.workflow.WorkflowStepHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 동화책 페이지들을 PDF로 조판한다. (Slice 2: 삽화는 placeholder 박스, 텍스트는 실제 문구)
 * 한글 렌더링을 위해 CJK TTF 폰트를 임베드한다 (기본: Windows 맑은 고딕).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PdfGenerationStepHandler implements WorkflowStepHandler {

    private static final float MARGIN = 40f;
    private static final float TITLE_SIZE = 20f;
    private static final float BODY_SIZE = 12f;
    private static final float LEADING = 18f;

    private final BookPageRepository bookPageRepository;
    private final LocalStorage localStorage;

    @Value("${storyai.pdf.korean-font-path:C:/Windows/Fonts/malgun.ttf}")
    private String koreanFontPath;

    @Override
    public WorkflowStep getStep() {
        return WorkflowStep.PDF_GENERATION;
    }

    @Override
    public void execute(VideoJob job) {
        List<BookPage> pages = bookPageRepository.findByVideoJobIdOrderByPageNumberAsc(job.getId());

        try (PDDocument doc = new PDDocument()) {
            PDFont font = loadFont(doc);

            writeCoverPage(doc, font, safeText(job.getGeneratedTitle(), "이야기책"));
            for (BookPage page : pages) {
                writeContentPage(doc, font, page);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            localStorage.write(localStorage.bookPdfPath(job.getId()), baos.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("PDF 생성 실패: " + e.getMessage(), e);
        }

        job.setResultUrl("/api/video-jobs/%d/download".formatted(job.getId()));
    }

    private PDFont loadFont(PDDocument doc) throws Exception {
        File fontFile = new File(koreanFontPath);
        if (!fontFile.exists()) {
            throw new IllegalStateException(
                    "한글 폰트를 찾을 수 없습니다: " + koreanFontPath + " (storyai.pdf.korean-font-path 설정 필요)");
        }
        return PDType0Font.load(doc, fontFile);
    }

    private void writeCoverPage(PDDocument doc, PDFont font, String title) throws Exception {
        PDPage page = new PDPage(PDRectangle.A5);
        doc.addPage(page);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            float y = page.getMediaBox().getHeight() / 2f;
            drawCentered(cs, font, title, TITLE_SIZE, page.getMediaBox().getWidth(), y);
        }
    }

    private void writeContentPage(PDDocument doc, PDFont font, BookPage bookPage) throws Exception {
        PDPage page = new PDPage(PDRectangle.A5);
        doc.addPage(page);

        float width = page.getMediaBox().getWidth();
        float height = page.getMediaBox().getHeight();
        float contentWidth = width - 2 * MARGIN;

        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            // 삽화 영역 (상단 절반) — 실제 이미지가 있으면 넣고, 없으면 placeholder 박스
            float boxHeight = height * 0.5f;
            float boxY = height - MARGIN - boxHeight;
            drawIllustration(doc, cs, bookPage, font, MARGIN, boxY, contentWidth, boxHeight, width);

            // 본문 텍스트 (박스 아래)
            float textTop = boxY - LEADING;
            List<String> lines = wrap(font, safeText(bookPage.getText(), ""), BODY_SIZE, contentWidth);
            float y = textTop;
            for (String line : lines) {
                if (y < MARGIN + LEADING) {
                    break; // 페이지 넘침 방지 (Slice 2 단순 처리)
                }
                drawLeft(cs, font, line, BODY_SIZE, MARGIN, y);
                y -= LEADING;
            }

            // 페이지 번호 (하단 중앙)
            drawCentered(cs, font, String.valueOf(bookPage.getPageNumber()), BODY_SIZE, width, MARGIN / 2f);
        }
    }

    /** 페이지 삽화를 박스 영역에 비율 유지하며 그린다. 이미지가 없으면 placeholder 박스. */
    private void drawIllustration(PDDocument doc, PDPageContentStream cs, BookPage bookPage, PDFont font,
                                  float x, float y, float boxW, float boxH, float pageWidth) throws Exception {
        byte[] bytes = localStorage.loadByUrl(bookPage.getImageUrl());
        if (bytes == null) {
            cs.addRect(x, y, boxW, boxH);
            cs.stroke();
            drawCentered(cs, font, "[ 삽화 " + bookPage.getPageNumber() + " ]", BODY_SIZE, pageWidth, y + boxH / 2f);
            return;
        }
        PDImageXObject image = PDImageXObject.createFromByteArray(doc, bytes, "page" + bookPage.getPageNumber());
        float scale = Math.min(boxW / image.getWidth(), boxH / image.getHeight());
        float w = image.getWidth() * scale;
        float h = image.getHeight() * scale;
        float ix = x + (boxW - w) / 2f;
        float iy = y + (boxH - h) / 2f;
        cs.drawImage(image, ix, iy, w, h);
    }

    private void drawLeft(PDPageContentStream cs, PDFont font, String text, float size, float x, float y) throws Exception {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    private void drawCentered(PDPageContentStream cs, PDFont font, String text, float size, float pageWidth, float y) throws Exception {
        float textWidth = font.getStringWidth(text) / 1000f * size;
        float x = (pageWidth - textWidth) / 2f;
        drawLeft(cs, font, text, size, x, y);
    }

    /** 폭에 맞춰 줄바꿈. 한글은 공백이 없을 수 있어 문자 단위로도 쪼갠다. */
    private List<String> wrap(PDFont font, String text, float size, float maxWidth) throws Exception {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            StringBuilder current = new StringBuilder();
            for (int i = 0; i < paragraph.length(); i++) {
                char c = paragraph.charAt(i);
                String candidate = current.toString() + c;
                float w = font.getStringWidth(candidate) / 1000f * size;
                if (w > maxWidth && current.length() > 0) {
                    lines.add(current.toString());
                    current = new StringBuilder().append(c);
                } else {
                    current.append(c);
                }
            }
            lines.add(current.toString());
        }
        return lines;
    }

    private String safeText(String s, String fallback) {
        if (s == null || s.isBlank()) {
            return fallback;
        }
        // 폰트에 없을 수 있는 제어문자 제거 (탭 등)
        return s.replace("\t", "  ").replace("\r", "");
    }
}
