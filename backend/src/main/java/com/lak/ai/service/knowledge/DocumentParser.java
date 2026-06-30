package com.lak.ai.service.knowledge;

import com.lak.ai.common.exception.KnowledgeException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * 文档解析器 — 将 TXT/PDF/DOCX 转换为带结构标记的纯文本。
 * <p>
 * PDF: PDFBox 提取文本。扫描件（无有效文本）不支持自动 OCR，
 * 请用 Umi-OCR / PaddleOCR GUI / 大模型等工具预处理为 TXT 后重新上传。
 * <p>
 * DOCX: Apache POI 段落遍历，Heading 样式转 Markdown 标题。
 */
@Slf4j
@Component
public class DocumentParser {

    public enum FileType { TXT, PDF, DOCX }

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
        byte[] pdfBytes = inputStream.readAllBytes();

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);

            if (text.trim().length() < 100) {
                throw new KnowledgeException(4_402,
                        "PDF 文件无有效文字内容，可能为扫描件或图片型 PDF。\n" +
                        "请使用 OCR 工具（Umi-OCR、PaddleOCR GUI 或大模型等）将文件转为 TXT 后重新上传。");
            }
            return text;
        } catch (KnowledgeException e) {
            throw e;
        } catch (IOException e) {
            throw new KnowledgeException(4_402, "PDF 文件解析失败，文件可能已损坏或加密", e);
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
