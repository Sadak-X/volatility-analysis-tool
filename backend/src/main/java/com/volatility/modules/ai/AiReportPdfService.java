package com.volatility.modules.ai;

import com.volatility.modules.ai.entity.AiAnalysisReportEntity;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Service;

@Service
public class AiReportPdfService {

    private static final float MARGIN = 54;
    private static final float BODY_SIZE = 11;
    private static final float TITLE_SIZE = 18;
    private static final float LINE_HEIGHT = 18;

    public byte[] build(String taskNo, AiAnalysisReportEntity report) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType0Font font = loadChineseFont(document);
            PdfWriter writer = new PdfWriter(document, font);
            writer.writeTitle("AI 波动率分析报告");
            writer.writeMeta("任务编号：" + taskNo);
            writer.writeMeta("股票代码：" + safe(report.getStockCode()));
            writer.writeMeta("分析模式：" + safe(report.getAnalysisMode()));
            writer.writeMeta("导出时间：" + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now()));
            writer.writeBlank();
            writer.writeParagraph("报告摘要：" + safe(report.getSummaryText()), BODY_SIZE, LINE_HEIGHT);
            writer.writeBlank();
            writer.writeMarkdown(safe(report.getFullReportMd()));
            writer.writeBlank();
            writer.writeParagraph(safe(report.getRiskDisclaimer()), BODY_SIZE, LINE_HEIGHT);
            writer.close();
            document.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("PDF 报告生成失败", ex);
        }
    }

    private PDType0Font loadChineseFont(PDDocument document) throws IOException {
        List<Path> candidates = List.of(
                Path.of("C:/Windows/Fonts/simhei.ttf"),
                Path.of("C:/Windows/Fonts/Deng.ttf"),
                Path.of("C:/Windows/Fonts/simsunb.ttf"),
                Path.of("C:/Windows/Fonts/SourceHanSansCN-Normal.ttf"),
                Path.of("C:/Windows/Fonts/NotoSansSC-VF.ttf")
        );
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                try {
                    return PDType0Font.load(document, candidate.toFile());
                } catch (IOException ignored) {

                }
            }
        }
        throw new IOException("未找到可用中文字体");
    }

    private String safe(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\uFE0F", "")
                .replace("✅", "[完成]")
                .replace("❌", "[失败]")
                .replace("⚠", "[警告]")
                .replace("📈", "[上行]")
                .replace("📉", "[下行]")
                .replace("📊", "[图表]")
                .replace("🔴", "[高]")
                .replace("🟢", "[低]")
                .replace("•", "-")
                .replace("·", "-")
                .replace("–", "-")
                .replace("—", "-")
                .replace("→", "->")
                .replace("←", "<-")
                .replace("≥", ">=")
                .replace("≤", "<=")
                .replace("≈", "~")
                .replace("△", "变化")
                .replace("□", "");
    }

    private static final class PdfWriter {
        private final PDDocument document;
        private final PDType0Font font;
        private PDPage page;
        private PDPageContentStream stream;
        private float y;
        private final float contentWidth;

        private PdfWriter(PDDocument document, PDType0Font font) throws IOException {
            this.document = document;
            this.font = font;
            this.contentWidth = PDRectangle.A4.getWidth() - MARGIN * 2;
            addPage();
        }

        private void writeTitle(String text) throws IOException {
            writeParagraph(text, TITLE_SIZE, 26);
        }

        private void writeMeta(String text) throws IOException {
            writeParagraph(text, BODY_SIZE, LINE_HEIGHT);
        }

        private void writeBlank() throws IOException {
            ensureSpace(LINE_HEIGHT);
            y -= LINE_HEIGHT / 2;
        }

        private void writeMarkdown(String markdown) throws IOException {
            for (String line : markdown.replace("\r\n", "\n").split("\n")) {
                String text = normalizeMarkdown(line);
                if (text.isBlank()) {
                    writeBlank();
                    continue;
                }
                boolean heading = line.trim().startsWith("#");
                writeParagraph(text, heading ? 14 : BODY_SIZE, heading ? 22 : LINE_HEIGHT);
            }
        }

        private String normalizeMarkdown(String line) {
            return line
                    .replaceFirst("^#{1,6}\\s*", "")
                    .replace("**", "")
                    .replace("__", "")
                    .replace("`", "")
                    .trim();
        }

        private void writeParagraph(String text, float fontSize, float lineHeight) throws IOException {
            for (String line : wrap(text, fontSize)) {
                ensureSpace(lineHeight);
                stream.beginText();
                stream.setFont(font, fontSize);
                stream.newLineAtOffset(MARGIN, y);
                stream.showText(line);
                stream.endText();
                y -= lineHeight;
            }
        }

        private List<String> wrap(String text, float fontSize) throws IOException {
            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (int offset = 0; offset < text.length();) {
                int codePoint = text.codePointAt(offset);
                String character = printableCharacter(codePoint);
                String candidate = current + character;
                if (!current.isEmpty() && stringWidth(candidate, fontSize) > contentWidth) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                current.append(character);
                offset += Character.charCount(codePoint);
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
            return lines.isEmpty() ? List.of("") : lines;
        }

        private float stringWidth(String text, float fontSize) throws IOException {
            return font.getStringWidth(text) / 1000 * fontSize;
        }

        private String printableCharacter(int codePoint) throws IOException {
            if (Character.isISOControl(codePoint)) {
                return " ";
            }
            String character = new String(Character.toChars(codePoint));
            try {
                font.getStringWidth(character);
                return character;
            } catch (IllegalArgumentException ex) {
                return " ";
            }
        }

        private void ensureSpace(float requiredHeight) throws IOException {
            if (y - requiredHeight >= MARGIN) {
                return;
            }
            stream.close();
            addPage();
        }

        private void addPage() throws IOException {
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = page.getMediaBox().getHeight() - MARGIN;
        }

        private void close() throws IOException {
            stream.close();
        }
    }
}
