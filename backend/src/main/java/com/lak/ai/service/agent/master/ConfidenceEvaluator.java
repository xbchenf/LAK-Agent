package com.lak.ai.service.agent.master;

import com.lak.ai.enums.IntentType;
import com.lak.ai.service.agent.master.IntentClassifier.ClassificationResult;
import com.lak.ai.service.rag.embedding.EmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * 置信度评估器 — 三层融合（JSON自评 + 规则加权 + 降级）。
 */
@Slf4j
@Service
public class ConfidenceEvaluator {

    private static final double THRESHOLD_HIGH = 0.7;
    private static final double THRESHOLD_LOW = 0.5;

    // 规则加权参数
    private static final double BOOST_LEGAL_TERM = 0.05;
    private static final double BOOST_STRUCTURE_MARK = 0.08;
    private static final double PENALTY_SHORT_MSG = 0.10;
    private static final double HARD_MATCH_THRESHOLD = 0.85;

    // 硬规则正则
    private static final Pattern DOC_NUMBER_PATTERN =
            Pattern.compile("[\\u4e00-\\u9fff]{1,5}[政发]\\s*〔\\d{4}〕\\s*\\d+\\s*号");
    private static final Pattern COMPLAINT_KEYWORD_PATTERN =
            Pattern.compile("投诉|举报|不作为|乱收费|出警慢|态度差|违规执法|我要告|我要投诉|暴力执法");
    private static final Pattern ARTICLE_PATTERN =
            Pattern.compile("第[一二三四五六七八九十百千]+[章节条款项]");

    // 公安领域术语表
    private static final String[] LEGAL_TERMS = {
            "治安管理", "交通管理", "户籍管理", "出入境", "身份证", "居住证",
            "特种行业", "消防安全", "禁毒", "网络安全", "枪支管理", "爆炸物品",
            "养犬管理", "流动人口", "出租房屋", "110接处警", "派出所", "交警",
            "刑事侦查", "电信诈骗", "赌博", "卖淫嫖娼", "盗窃", "故意伤害",
            "寻衅滋事", "危险驾驶", "酒后驾车", "超速行驶", "肇事逃逸"
    };

    private final EmbeddingService embeddingService;

    public ConfidenceEvaluator(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    /**
     * 三层置信度评估。
     */
    public ConfidenceResult evaluate(ClassificationResult classification, String userMessage) {
        // 1. 响应层校验
        if (classification.rawResponse() == null || classification.rawResponse().isBlank()) {
            return new ConfidenceResult(IntentType.UNKNOWN, 0.0, "模型调用失败", true);
        }
        if (classification.intentType() == null || classification.intentType() == IntentType.UNKNOWN) {
            return new ConfidenceResult(IntentType.UNKNOWN, classification.modelConfidence(),
                    "无法识别意图", true);
        }

        // 2. 硬规则先于模型
        HardRuleResult hardRule = checkHardRules(userMessage);
        if (hardRule.matched) {
            log.debug("硬规则命中, intent={}, confidence={}", hardRule.intentType, hardRule.confidence);
            return new ConfidenceResult(hardRule.intentType, hardRule.confidence,
                    "硬规则精确匹配", false);
        }

        // 3. 模型层 + 规则加权
        double modelConfidence = classification.modelConfidence();
        double ruleAdjustment = calculateRuleAdjustment(userMessage, classification.intentType());
        double adjusted = clamp(modelConfidence + ruleAdjustment, 0.0, 1.0);

        boolean isFallback = adjusted < THRESHOLD_LOW;
        boolean needsVoting = adjusted >= THRESHOLD_LOW && adjusted < THRESHOLD_HIGH;

        log.debug("置信度评估: model={}, adjustment={}, final={}, needsVoting={}",
                modelConfidence, ruleAdjustment, adjusted, needsVoting);

        return new ConfidenceResult(classification.intentType(), adjusted,
                classification.reasoning(), isFallback);
    }

    private double calculateRuleAdjustment(String userMessage, IntentType intentType) {
        double adjustment = 0.0;

        // 政法术语 Boost
        for (String term : LEGAL_TERMS) {
            if (userMessage.contains(term)) {
                adjustment += BOOST_LEGAL_TERM;
                break; // 只加一次
            }
        }

        // 结构标记 Boost（第X条）
        if (ARTICLE_PATTERN.matcher(userMessage).find()) {
            adjustment += BOOST_STRUCTURE_MARK;
        }

        // 短消息惩罚
        if (userMessage.length() < 5) {
            boolean hasTerm = false;
            for (String term : LEGAL_TERMS) {
                if (userMessage.contains(term)) { hasTerm = true; break; }
            }
            if (!hasTerm) {
                adjustment -= PENALTY_SHORT_MSG;
            }
        }

        return adjustment;
    }

    private HardRuleResult checkHardRules(String userMessage) {
        // 发文字号精确匹配 → 锁定 POLICY_CONSULT
        if (DOC_NUMBER_PATTERN.matcher(userMessage).find()) {
            return new HardRuleResult(true, IntentType.POLICY_CONSULT, HARD_MATCH_THRESHOLD);
        }
        // 投诉关键词 → 锁定 COMPLAINT_SUGGEST
        if (COMPLAINT_KEYWORD_PATTERN.matcher(userMessage).find()) {
            return new HardRuleResult(true, IntentType.COMPLAINT_SUGGEST, HARD_MATCH_THRESHOLD);
        }
        return new HardRuleResult(false, null, 0.0);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record HardRuleResult(boolean matched, IntentType intentType, double confidence) {}

    public record ConfidenceResult(IntentType intentType, double confidence,
                                    String detail, boolean fallback) {
        public boolean needsVoting() {
            return !fallback && confidence >= THRESHOLD_LOW && confidence < THRESHOLD_HIGH;
        }
    }
}
