package com.lak.ai.service.knowledge;

import com.lak.ai.common.exception.KnowledgeException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * 文档解析器 — 将 TXT/PDF/DOCX 转换为带结构标记的纯文本。
 * <p>
 * PDF 策略: PDFBox 电子文本 → 无文本则 RapidOCR CLI 兜底（扫描件）。
 * DOCX 策略: Apache POI 段落遍历，Heading 样式转 Markdown 标题。
 */
@Slf4j
@Component
public class DocumentParser {

    public enum FileType { TXT, PDF, DOCX }

    /**
     * 从文件扩展名推断类型。
     */
    public static FileType detectType(String filename) {
        if (filename == null) {
            throw new KnowledgeException(4_401, "文件名不能为空，仅支持 txt/pdf/docx");
        }
        String name = filename.toLowerCase();
        if (name.endsWith(".pdf")) return FileType.PDF;
        if (name.endsWith(".docx")) return FileType.DOCX;
        if (name.endsWith(".txt")) return FileType.TXT;
        throw new KnowledgeException(4_401, "不支持的文件格式: " + filename + "，仅支持 txt/pdf/docx");
    }

    /**
     * 解析文件流，返回结构化纯文本。
     */
    public String parse(InputStream inputStream, FileType fileType, String filename) throws IOException {
        return switch (fileType) {
            case TXT -> parseTxt(inputStream);
            case PDF -> parsePdf(inputStream);
            case DOCX -> parseDocx(inputStream);
        };
    }

    // === TXT ===

    private String parseTxt(InputStream inputStream) throws IOException {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    // === PDF ===

    private String parsePdf(InputStream inputStream) throws IOException {
        // PDFBox 需要可重读的流 — 拷贝到 byte[]
        byte[] pdfBytes = inputStream.readAllBytes();

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);

            // 判断是否为扫描件：文本量 < 文件大小 × 0.3
            if (text.trim().length() < pdfBytes.length * 0.3) {
                log.info("PDF 文本量过少 ({}chars vs {}bytes)，判定为扫描件，走 OCR", text.length(), pdfBytes.length);
                return ocrPdf(pdfBytes);
            }
            return text;
        } catch (IOException e) {
            throw new KnowledgeException(4_402, "PDF 文件解析失败，文件可能已损坏或加密", e);
        }
    }

    /**
     * RapidOCR CLI 兜底：逐页渲染为图片 → OCR 识别。
     */
    private String ocrPdf(byte[] pdfBytes) throws IOException {
        StringBuilder result = new StringBuilder();
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pages = document.getNumberOfPages();
            log.info("RapidOCR 开始处理 {} 页扫描件", pages);

            Path tempDir = Files.createTempDirectory("pdf-ocr-");
            try {
                for (int i = 0; i < pages; i++) {
                    BufferedImage image = renderer.renderImageWithDPI(i, 300);
                    Path imageFile = tempDir.resolve(String.format("page_%03d.png", i + 1));
                    ImageIO.write(image, "png", imageFile.toFile());

                    String pageText = runRapidOcr(imageFile);
                    result.append(pageText).append("\n");
                }
            } finally {
                // 清理临时文件
                File[] files = tempDir.toFile().listFiles();
                if (files != null) {
                    for (File f : files) f.delete();
                }
                Files.deleteIfExists(tempDir);
            }
        }
        return result.toString();
    }

    private String runRapidOcr(Path imageFile) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "rapidocr", "--image_path", imageFile.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(120, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("RapidOCR 超时 (120s), 文件: {}", imageFile);
            }
            return output.toString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("RapidOCR 被中断", e);
        } catch (IOException e) {
            log.error("RapidOCR CLI 调用失败，请确认 rapidocr 已安装并在 PATH 中", e);
            throw new KnowledgeException(4_402, "OCR 识别失败（RapidOCR 不可用），请确认扫描件质量", e);
        }
    }

    // === DOCX ===

    private String parseDocx(InputStream inputStream) throws IOException {
        StringBuilder result = new StringBuilder();
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            document.getParagraphs().forEach(paragraph -> {
                String styleId = paragraph.getStyleID();
                String text = paragraph.getText();
                if (text == null || text.isBlank()) {
                    result.append("\n");
                    return;
                }

                // Heading 样式 → Markdown 标题标记
                if (styleId != null) {
                    if (styleId.contains("Heading1") || styleId.equals("1")) {
                        result.append("# ").append(text).append("\n\n");
                        return;
                    }
                    if (styleId.contains("Heading2") || styleId.equals("2")) {
                        result.append("## ").append(text).append("\n\n");
                        return;
                    }
                    if (styleId.contains("Heading3") || styleId.equals("3")) {
                        result.append("### ").append(text).append("\n\n");
                        return;
                    }
                }
                // 尝试检测中文标题模式（如 "第一章"、"第一条"、"一、"）
                String trimmed = text.trim();
                if (trimmed.matches("^第[一二三四五六七八九十百千]+[章节条].*")) {
                    result.append("## ").append(trimmed).append("\n\n");
                } else if (trimmed.matches("^[一二三四五六七八九十]、.*")) {
                    result.append("### ").append(trimmed).append("\n\n");
                } else {
                    result.append(text).append("\n");
                }
            });
        } catch (IOException e) {
            throw new KnowledgeException(4_402, "DOCX 文件解析失败", e);
        }
        return result.toString();
    }
}
