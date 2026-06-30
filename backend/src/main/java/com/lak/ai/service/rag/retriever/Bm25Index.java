package com.lak.ai.service.rag.retriever;

import com.lak.ai.model.bo.RagFragment;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BM25 倒排索引 — JVM 内存实现，中文逐字分词。
 * <p>
 * 启动时从 Qdrant 回放已发布文档重建索引，避免 JVM 重启丢失。
 */
@Slf4j
@Component
public class Bm25Index {

    private static final double K1 = 1.5;
    private static final double B = 0.75;

    private final Map<String, String> documents = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Integer>> termFreqs = new ConcurrentHashMap<>();
    private final Map<String, Integer> docFreqs = new ConcurrentHashMap<>();
    private int totalDocs = 0;
    private double avgDocLength = 100;

    public synchronized void index(String docId, String text) {
        if (text == null || text.isBlank()) return;
        documents.put(docId, text);
        Map<String, Integer> tf = computeTf(text);
        Map<String, Integer> oldTf = termFreqs.put(docId, tf);
        if (oldTf != null) {
            for (String term : oldTf.keySet()) {
                if (!tf.containsKey(term)) docFreqs.computeIfPresent(term, (k, v) -> v > 1 ? v - 1 : null);
            }
        }
        for (String term : tf.keySet()) {
            if (oldTf == null || !oldTf.containsKey(term)) docFreqs.merge(term, 1, Integer::sum);
        }
        totalDocs = documents.size();
        avgDocLength = documents.values().stream().mapToInt(String::length).average().orElse(100);
    }

    public synchronized void remove(String docId) {
        Map<String, Integer> oldTf = termFreqs.remove(docId);
        if (oldTf != null) {
            for (String term : oldTf.keySet()) docFreqs.computeIfPresent(term, (k, v) -> v > 1 ? v - 1 : null);
        }
        documents.remove(docId);
        totalDocs = documents.size();
        avgDocLength = documents.values().stream().mapToInt(String::length).average().orElse(100);
    }

    public List<RagFragment> search(String query, int topK) {
        if (totalDocs == 0 || query == null || query.isBlank()) return List.of();
        Map<String, Double> scores = new HashMap<>();
        for (var entry : documents.entrySet()) {
            String docId = entry.getKey();
            String text = entry.getValue();
            int docLen = text.length();
            Map<String, Integer> tf = termFreqs.getOrDefault(docId, Map.of());
            double score = 0;
            for (int i = 0; i < query.length(); i++) {
                char c = query.charAt(i);
                String term;
                if (c > 127) { term = String.valueOf(c); }
                else if (Character.isLetterOrDigit(c)) {
                    int j = i; while (j < query.length() && Character.isLetterOrDigit(query.charAt(j))) j++;
                    term = query.substring(i, j).toLowerCase(); i = j - 1;
                } else continue;
                int f = tf.getOrDefault(term, 0);
                int df = docFreqs.getOrDefault(term, 0);
                if (f > 0 && df > 0) {
                    double idf = Math.log((totalDocs - df + 0.5) / (df + 0.5) + 1.0);
                    score += idf * (f * (K1 + 1)) / (f + K1 * (1 - B + B * docLen / avgDocLength));
                }
            }
            if (score > 0) scores.put(docId, score);
        }
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> RagFragment.builder()
                        .docId(e.getKey())
                        .text(documents.getOrDefault(e.getKey(), ""))
                        .score(Math.min(e.getValue() / 10.0, 1.0))
                        .build())
                .toList();
    }

    public int getDocCount() { return totalDocs; }

    private Map<String, Integer> computeTf(String text) {
        Map<String, Integer> tf = new HashMap<>();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String term;
            if (c > 127) { term = String.valueOf(c); }
            else if (Character.isLetterOrDigit(c)) {
                int j = i; while (j < text.length() && Character.isLetterOrDigit(text.charAt(j))) j++;
                term = text.substring(i, j).toLowerCase(); i = j - 1;
            } else continue;
            tf.merge(term, 1, Integer::sum);
        }
        return tf;
    }
}
