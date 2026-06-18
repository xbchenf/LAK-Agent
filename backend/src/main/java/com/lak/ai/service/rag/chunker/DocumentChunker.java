package com.lak.ai.service.rag.chunker;

import com.lak.ai.model.bo.ChunkResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档分块器 — 结构感知段落分块。
 * <p>
 * 政策法规: 按"第X条"锚点切分。
 * 办事指南: 按标题锚点（"一、"、"办理条件："）切分。
 * <p>
 * 参数: 主块 500-1000字符, 最小200, 最大1500, 重叠150字符。
 */
@Slf4j
@Service
public class DocumentChunker {

    private static final int CHUNK_SIZE = 800;
    private static final int MIN_SIZE = 200;
    private static final int MAX_SIZE = 1500;
    private static final int OVERLAP = 150;

    private static final Pattern ARTICLE_PATTERN = Pattern.compile(
            "第[一二三四五六七八九十百千]+[章节条款项]");
    private static final Pattern SECTION_PATTERN = Pattern.compile(
            "[一二三四五六七八九十]+、|（[一二三四五六七八九十]）|(?:办理|申请|提交|领取|缴纳|携带|出具)[^，。；]+[：:]");

    /**
     * 分块入口 — 根据文档类型自动选择切分策略。
     */
    public List<ChunkResult> chunk(String text, String docId, String docTitle,
                                    String docType, String sourceNo, String effectiveDate) {
        if ("POLICY".equalsIgnoreCase(docType)) {
            return chunkByArticle(text, docId, docTitle, docType, sourceNo, effectiveDate);
        }
        return chunkBySection(text, docId, docTitle, docType, sourceNo, effectiveDate);
    }

    /**
     * 政策法规分块 — 按"第X条"锚点。
     */
    private List<ChunkResult> chunkByArticle(String text, String docId, String docTitle,
                                              String docType, String sourceNo, String effectiveDate) {
        List<String> segments = splitByPattern(text, ARTICLE_PATTERN);
        return buildChunks(segments, docId, docTitle, docType, sourceNo, effectiveDate);
    }

    /**
     * 办事指南分块 — 按章节标题锚点。
     */
    private List<ChunkResult> chunkBySection(String text, String docId, String docTitle,
                                              String docType, String sourceNo, String effectiveDate) {
        List<String> segments = splitByPattern(text, SECTION_PATTERN);
        return buildChunks(segments, docId, docTitle, docType, sourceNo, effectiveDate);
    }

    /**
     * 按正则锚点切分文本。
     */
    private List<String> splitByPattern(String text, Pattern pattern) {
        List<String> segments = new ArrayList<>();
        Matcher m = pattern.matcher(text);
        List<Integer> boundaries = new ArrayList<>();
        boundaries.add(0);
        while (m.find()) {
            if (m.start() > 0) {
                boundaries.add(m.start());
            }
        }
        boundaries.add(text.length());

        for (int i = 0; i < boundaries.size() - 1; i++) {
            String seg = text.substring(boundaries.get(i), boundaries.get(i + 1)).trim();
            if (!seg.isEmpty()) {
                segments.add(seg);
            }
        }
        // fallback — 无结构标记时按固定大小切分
        if (segments.isEmpty() && !text.isBlank()) {
            segments = splitBySize(text);
        }
        return segments;
    }

    /**
     * 固定大小切分（兜底策略）。
     */
    private List<String> splitBySize(String text) {
        List<String> segments = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + CHUNK_SIZE, text.length());
            // 尽量在句子边界断开
            if (end < text.length()) {
                int lastPeriod = text.lastIndexOf('。', end);
                if (lastPeriod > start + MIN_SIZE) {
                    end = lastPeriod + 1;
                }
            }
            segments.add(text.substring(start, end).trim());
            start = end;
        }
        return segments;
    }

    /**
     * 从段落列表构建带元数据的 ChunkResult。
     */
    private List<ChunkResult> buildChunks(List<String> segments, String docId, String docTitle,
                                           String docType, String sourceNo, String effectiveDate) {
        // 合并短段、拆分长段
        List<String> merged = mergeShortSegments(segments);
        List<String> finalChunks = splitLongSegments(merged);

        List<ChunkResult> results = new ArrayList<>();
        String prevOverlap = "";
        for (int i = 0; i < finalChunks.size(); i++) {
            String chunkText = finalChunks.get(i);
            results.add(ChunkResult.builder()
                    .text(chunkText)
                    .index(i)
                    .totalChunks(finalChunks.size())
                    .docId(docId)
                    .docTitle(docTitle)
                    .docType(docType)
                    .sourceNo(sourceNo)
                    .effectiveDate(effectiveDate)
                    .prevChunkId(i > 0 ? docId + "-" + (i - 1) : null)
                    .nextChunkId(i < finalChunks.size() - 1 ? docId + "-" + (i + 1) : null)
                    .overlapPrefix(prevOverlap)
                    .version(1)
                    .build());
            // 提取下一次的重叠前缀
            if (chunkText.length() > OVERLAP) {
                prevOverlap = chunkText.substring(chunkText.length() - OVERLAP);
            } else {
                prevOverlap = chunkText;
            }
        }
        return results;
    }

    /**
     * 合并短段落（< MIN_SIZE）。
     */
    private List<String> mergeShortSegments(List<String> segments) {
        List<String> result = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (String seg : segments) {
            if (buf.length() + seg.length() < CHUNK_SIZE) {
                if (!buf.isEmpty()) buf.append("\n");
                buf.append(seg);
            } else {
                if (!buf.isEmpty()) {
                    result.add(buf.toString());
                    buf = new StringBuilder();
                }
                if (seg.length() < MIN_SIZE && !result.isEmpty()) {
                    // 追加到上一块
                    result.set(result.size() - 1, result.get(result.size() - 1) + "\n" + seg);
                } else {
                    buf.append(seg);
                }
            }
        }
        if (!buf.isEmpty()) {
            result.add(buf.toString());
        }
        return result;
    }

    /**
     * 拆分长段落（> MAX_SIZE）。
     */
    private List<String> splitLongSegments(List<String> segments) {
        List<String> result = new ArrayList<>();
        for (String seg : segments) {
            if (seg.length() <= MAX_SIZE) {
                result.add(seg);
            } else {
                int start = 0;
                while (start < seg.length()) {
                    int end = Math.min(start + CHUNK_SIZE, seg.length());
                    if (end < seg.length()) {
                        int lastPeriod = seg.lastIndexOf('。', end);
                        if (lastPeriod > start + MIN_SIZE) {
                            end = lastPeriod + 1;
                        }
                    }
                    result.add(seg.substring(start, end).trim());
                    start = Math.max(end - OVERLAP, start + MIN_SIZE);
                }
            }
        }
        return result;
    }
}
