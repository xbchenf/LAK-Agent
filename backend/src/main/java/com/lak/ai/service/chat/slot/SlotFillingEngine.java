package com.lak.ai.service.chat.slot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lak.ai.enums.SessionStatus;
import com.lak.ai.model.bo.SlotDefinition;
import com.lak.ai.model.bo.SlotExtractionResult;
import com.lak.ai.service.chat.session.SessionManager;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Slot-Filling 引擎 — 多轮对话工单信息采集。
 * <p>
 * 采用业界主流的 FSM + LLM 混合架构：
 * <ul>
 *   <li>FSM 管流程（当前槽位、轮次限制、状态转换）</li>
 *   <li>LLM 管理解（从噪声输入中提取槽值、识别意图切换、修改请求）</li>
 *   <li>JSON Schema 校验保证 LLM 输出可靠</li>
 *   <li>自然语言摘要（NL-DST）替代键值对作为 LLM 上下文</li>
 * </ul>
 * <p>
 * 支持：打断挂起恢复、槽位修改、冗余输入清洗、主动取消、早期提交。
 */
@Slf4j
@Service
public class SlotFillingEngine {

    private static final int MAX_FILL_ROUNDS = 5;
    private static final int MAX_INTERRUPT_ROUNDS = 3;
    private static final String FIELD_SLOTS = "slotValues";
    private static final String FIELD_CURRENT_SLOT = "currentSlot";
    private static final String FIELD_FILL_ROUND = "fillRound";
    private static final String FIELD_NL_SUMMARY = "nlSummary";
    private static final String FIELD_INTERRUPTION = "interruption";
    private static final String SESSION_PREFIX = "session:";

    /** 规则预清洗 — 去口头前缀 */
    private static final Pattern NOISE_PREFIX = Pattern.compile("^[我我们]?是?叫?\\s*");
    /** 手机号正则（直提，不走 LLM） */
    private static final Pattern PHONE_PATTERN = Pattern.compile("(1[3-9]\\d{9})");
    /** 提交关键词 — 必填齐全时触发早期完成 */
    private static final Pattern SUBMIT_PATTERN = Pattern.compile("^(提交|完成|好了|可以了|就这样|不填了|没了|没有了|就这些)\\s*$");
    /** 取消关键词 */
    private static final Pattern CANCEL_PATTERN = Pattern.compile("^(算了|不填了|取消|不用了|放弃)\\s*$");
    /** 用户主动要求转手工填写 */
    private static final Pattern REDIRECT_MANUAL_PATTERN = Pattern.compile(".*(?:我自己填|去页面填|手工填|表单填|网页填|跳转|打开页面).*");

    /** 投诉类型 → 标签映射 */
    private static final Map<String, String> COMPLAINT_TYPE_MAP;

    static {
        COMPLAINT_TYPE_MAP = Map.ofEntries(
                Map.entry("1", "治安投诉"), Map.entry("第一", "治安投诉"),
                Map.entry("第1", "治安投诉"), Map.entry("第1类", "治安投诉"),
                Map.entry("2", "窗口服务投诉"), Map.entry("第二", "窗口服务投诉"),
                Map.entry("第2", "窗口服务投诉"), Map.entry("第2类", "窗口服务投诉"),
                Map.entry("3", "派出所/民警投诉"), Map.entry("第三", "派出所/民警投诉"),
                Map.entry("第3", "派出所/民警投诉"), Map.entry("第3类", "派出所/民警投诉"),
                Map.entry("4", "其他"), Map.entry("第四", "其他"),
                Map.entry("第4", "其他"), Map.entry("第4类", "其他")
        );
    }

    private final SessionManager sessionManager;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ChatModel chatModel;
    private final SlotExtractionValidator validator;

    private final List<SlotDefinition> slotDefinitions = buildSlotDefinitions();

    public SlotFillingEngine(SessionManager sessionManager,
                             StringRedisTemplate redisTemplate,
                             ObjectMapper objectMapper,
                             ChatModel chatModel) {
        this.sessionManager = sessionManager;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.chatModel = chatModel;
        this.validator = new SlotExtractionValidator();
    }

    private List<SlotDefinition> buildSlotDefinitions() {
        return List.of(
                SlotDefinition.builder()
                        .name("complaintType").label("投诉类型").required(true)
                        .promptTemplate("请问您要反馈的是哪类问题？")
                        .build(),
                SlotDefinition.builder()
                        .name("contactPhone").label("联系电话").required(true)
                        .promptTemplate("请留下您的联系电话：")
                        .validationPattern("^1[3-9]\\d{9}$")
                        .validationMessage("手机号格式不正确，请输入11位手机号")
                        .build(),
                SlotDefinition.builder()
                        .name("description").label("问题描述").required(true)
                        .promptTemplate("请详细描述您的问题：")
                        .build()
        );
    }

    // ==================== 入口 ====================

    /** 初始化 Slot-Filling，返回第一个槽位的追问话术 */
    public String startFilling(String sessionId) {
        clearSlots(sessionId);
        setCurrentSlot(sessionId, 0);
        setFillRound(sessionId, 1);
        clearNlSummary(sessionId);
        clearInterruption(sessionId);
        sessionManager.transition(sessionId, SessionStatus.COLLECT_INFO);
        SlotDefinition first = slotDefinitions.get(0);
        log.info("Slot-Filling 开始, sessionId={}, firstSlot={}", sessionId, first.getName());
        return first.getPromptTemplate();
    }

    /**
     * 处理用户在 COLLECT_INFO 状态下的一条消息。
     * <p>
     * 核心管道：预清洗 → LLM 抽取 → JSON 校验 → 动作分发 → 完成检测。
     *
     * @return FillingResult — 调用方按类型处理结果
     */
    public FillingResult processResponse(String sessionId, String userMessage) {
        // === Step 0: 恢复打断状态 ===
        if (isInterrupted(sessionId) && !isChitchatFollowup(userMessage)) {
            // 用户回到工单话题 — 清除打断状态
            clearInterruption(sessionId);
        }

        // === Step 1: 规则预清洗 ===
        String cleaned = NOISE_PREFIX.matcher(userMessage.trim()).replaceFirst("");

        // 取消检测
        if (CANCEL_PATTERN.matcher(userMessage.trim()).matches()) {
            sessionManager.transition(sessionId, SessionStatus.CLOSED);
            log.info("用户取消工单填写, sessionId={}", sessionId);
            return FillingResult.cancelled();
        }

        // 提交检测：必填齐全 + 用户说"提交"
        Map<String, String> currentSlots = getAllSlotValues(sessionId);
        if (allRequiredFilled(currentSlots) && SUBMIT_PATTERN.matcher(userMessage.trim()).matches()) {
            return completeFilling(sessionId);
        }

        // 手机号正则直提（LLM 兜底也做，但正则先抓一把）
        String phoneDirect = null;
        SlotDefinition currentSlot = getCurrentSlotDef(sessionId);
        if (currentSlot != null && "contactPhone".equals(currentSlot.getName())) {
            var pm = PHONE_PATTERN.matcher(userMessage);
            if (pm.find()) {
                phoneDirect = pm.group(1);
            }
        }

        // === Step 2: LLM 结构化抽取 ===
        SlotExtractionResult extraction = extractWithRetry(sessionId, userMessage, cleaned, phoneDirect);

        // === Step 3: 按 action 分发 ===
        return dispatch(sessionId, userMessage, extraction, currentSlots);
    }

    /** 获取所有已填充的槽位值 */
    public Map<String, String> getAllSlotValues(String sessionId) {
        String json = (String) redisTemplate.opsForHash().get(SESSION_PREFIX + sessionId, FIELD_SLOTS);
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyMap();
        }
    }

    /** 检查中断状态是否激活 */
    public boolean isInterrupted(String sessionId) {
        String json = (String) redisTemplate.opsForHash().get(SESSION_PREFIX + sessionId, FIELD_INTERRUPTION);
        if (json == null) return false;
        try {
            var node = objectMapper.readTree(json);
            return node.has("active") && node.get("active").asBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    /** 获取中断轮次 */
    public int getInterruptRounds(String sessionId) {
        String json = (String) redisTemplate.opsForHash().get(SESSION_PREFIX + sessionId, FIELD_INTERRUPTION);
        if (json == null) return 0;
        try {
            var node = objectMapper.readTree(json);
            return node.has("rounds") ? node.get("rounds").asInt() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /** 中断轮次+1；超过上限返回 true（强制回归） */
    public boolean incrementInterruptRounds(String sessionId) {
        int rounds = getInterruptRounds(sessionId) + 1;
        setInterruption(sessionId, true, rounds, getCurrentSlot(sessionId));
        return rounds >= MAX_INTERRUPT_ROUNDS;
    }

    /** 获取当前槽位的回钩提示 */
    public String getResumePrompt(String sessionId) {
        SlotDefinition slot = getCurrentSlotDef(sessionId);
        if (slot == null) return "";
        return "\n\n（如需继续咨询请追问，否则我将回到工单填写）";
    }

    // ==================== LLM 抽取 ====================

    /** 调用 LLM 提取槽位信息，JSON 校验失败时 reprompt 最多 2 次 */
    private SlotExtractionResult extractWithRetry(String sessionId, String userMessage,
                                                   String cleaned, String phoneDirect) {
        String nlSummary = getNlSummary(sessionId);
        SlotDefinition currentSlot = getCurrentSlotDef(sessionId);
        String slotLabel = currentSlot != null ? currentSlot.getLabel() : "未知";
        String slotName = currentSlot != null ? currentSlot.getName() : "unknown";

        String prompt = buildExtractionPrompt(slotLabel, slotName, nlSummary, userMessage);

        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                String raw = chatModel.chat(prompt);
                SlotExtractionResult result = validator.parseAndValidate(raw, slotName);

                // 手机号直提优先
                if (phoneDirect != null && "fill".equals(result.action())
                        && "contactPhone".equals(slotName)) {
                    result = new SlotExtractionResult("fill", slotName, phoneDirect,
                            Math.max(result.confidence(), 0.95), "regex+llm");
                }
                // 投诉类型规范化
                if ("complaintType".equals(slotName) && "fill".equals(result.action())
                        && result.extractedValue() != null) {
                    result = new SlotExtractionResult("fill", slotName,
                            normalizeComplaintType(result.extractedValue()),
                            result.confidence(), result.reasoning());
                }
                return result;
            } catch (SlotExtractionValidator.ExtractionParseException e) {
                if (attempt < 2) {
                    prompt = prompt + "\n\n上次输出校验失败：" + e.getMessage()
                            + "\n请严格按 JSON 格式重新输出。";
                    log.debug("LLM 抽取校验失败，reprompt #{}, sessionId={}, error={}",
                            attempt + 1, sessionId, e.getMessage());
                } else {
                    log.warn("LLM 抽取校验失败达上限, sessionId={}, error={}", sessionId, e.getMessage());
                    return fallbackExtraction(userMessage, cleaned, slotName, phoneDirect);
                }
            } catch (Exception e) {
                // LLM 调用失败（网络/API key 缺失等）→ 降级为规则提取
                log.warn("LLM 调用失败，降级为规则提取, sessionId={}, error={}", sessionId, e.getMessage());
                return fallbackExtraction(userMessage, cleaned, slotName, phoneDirect);
            }
        }
        return fallbackExtraction(userMessage, cleaned, slotName, phoneDirect);
    }

    /** LLM 不可用时的规则兜底提取 */
    private SlotExtractionResult fallbackExtraction(String rawMessage, String cleaned,
                                                     String slotName, String phoneDirect) {
        String msg = cleaned != null ? cleaned.trim() : rawMessage.trim();

        // 取消检测
        if (CANCEL_PATTERN.matcher(msg).matches()) {
            return new SlotExtractionResult("cancel", null, null, 0.9, "regex:cancel");
        }
        // 提交检测
        if (SUBMIT_PATTERN.matcher(msg).matches()) {
            return new SlotExtractionResult("fill", slotName, msg, 0.8, "regex:submit");
        }
        // 闲聊关键词
        if (isChitchatKeyword(msg)) {
            return new SlotExtractionResult("chitchat", null, null, 0.7, "regex:chitchat");
        }
        // 新意图关键词
        if (msg.contains("违法") || msg.contains("怎么办") || msg.contains("政策")
                || msg.contains("法律") || msg.contains("规定") || msg.contains("罚款")) {
            return new SlotExtractionResult("new_intent", null, null, 0.6, "regex:new_intent");
        }
        // 用户主动要求转手工填写
        if (REDIRECT_MANUAL_PATTERN.matcher(msg).matches()) {
            return new SlotExtractionResult("cancel", null, null, 0.9, "regex:redirect_manual");
        }
        // 修改关键词
        if (msg.matches("^(改|修改|换|更换|更新|纠正).*") || msg.contains("错了") || msg.contains("不对")) {
            return new SlotExtractionResult("modify", slotName, null, 0.7, "regex:modify");
        }
        // 手机号直提
        if ("contactPhone".equals(slotName) && phoneDirect != null) {
            return new SlotExtractionResult("fill", slotName, phoneDirect, 0.95, "regex:phone");
        }
        // 默认当 fill — 投诉类型先规范化
        if ("complaintType".equals(slotName)) {
            return new SlotExtractionResult("fill", slotName,
                    normalizeComplaintType(msg), 0.6, "regex:default");
        }
        return new SlotExtractionResult("fill", slotName, msg, 0.6, "regex:default");
    }

    /** 规范化投诉类型 — 把"第3类"/"3"/"第三"映射到标准标签 */
    private String normalizeComplaintType(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        // 精确匹配枚举映射
        String mapped = COMPLAINT_TYPE_MAP.get(trimmed);
        if (mapped != null) return mapped;
        // 包含数字的模糊匹配："第3类投诉" → "派出所/民警投诉"
        for (var entry : COMPLAINT_TYPE_MAP.entrySet()) {
            if (trimmed.contains(entry.getKey())) return entry.getValue();
        }
        // 已有的标准标签直接返回
        for (String label : COMPLAINT_TYPE_MAP.values()) {
            if (trimmed.contains(label)) return label;
        }
        return trimmed;
    }

    private boolean isChitchatKeyword(String msg) {
        String m = msg.toLowerCase();
        return m.contains("天气") || m.contains("你好") || m.contains("谢谢")
                || m.contains("吃饭") || m.contains("再见") || m.contains("哈哈");
    }

    private String buildExtractionPrompt(String slotLabel, String slotName,
                                          String nlSummary, String userMessage) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是工单信息提取助手。当前收集槽位：").append(slotLabel)
                .append("（").append(slotName).append("）。\n");
        if (nlSummary != null && !nlSummary.isBlank()) {
            sb.append("已收集信息：").append(nlSummary).append("\n");
        }
        sb.append("用户消息：\"").append(userMessage).append("\"\n\n");
        sb.append("请判断意图并提取信息，严格输出JSON（不要额外文字）：\n");
        sb.append("{\n");
        sb.append("  \"action\": \"fill|modify|chitchat|new_intent|cancel\",\n");
        sb.append("  \"targetSlot\": \"").append(slotName).append("\",\n");
        sb.append("  \"extractedValue\": \"提取的值\",\n");
        sb.append("  \"confidence\": 0.95,\n");
        sb.append("  \"reasoning\": \"简短判断\"\n");
        sb.append("}\n\n");
        sb.append("规则：1)口头前缀去掉 2)手机号11位1开头 3)修改意图用modify 4)闲聊用chitchat 5)政策法律用new_intent 6)放弃用cancel");
        return sb.toString();
    }

    // ==================== 动作分发 ====================

    private FillingResult dispatch(String sessionId, String userMessage,
                                    SlotExtractionResult extraction,
                                    Map<String, String> currentSlots) {
        return switch (extraction.action()) {
            case "fill"    -> handleFill(sessionId, extraction, currentSlots);
            case "modify"  -> handleModify(sessionId, extraction);
            case "chitchat" -> handleChitchat(sessionId);
            case "new_intent" -> handleNewIntent(sessionId, extraction);
            case "cancel"  -> {
                sessionManager.transition(sessionId, SessionStatus.CLOSED);
                yield FillingResult.cancelled();
            }
            default -> handleFill(sessionId, extraction, currentSlots); // 兜底当 fill 处理
        };
    }

    /** 填充槽位 → 推进 */
    private FillingResult handleFill(String sessionId, SlotExtractionResult extraction,
                                      Map<String, String> currentSlots) {
        String slotName = extraction.targetSlot() != null
                ? extraction.targetSlot()
                : getCurrentSlotDef(sessionId).getName();
        String value = extraction.extractedValue();

        // 投诉类型规范化
        if ("complaintType".equals(slotName) && value != null) {
            value = normalizeComplaintType(value);
        }

        // 写入槽值
        setSlotValue(sessionId, slotName, value != null ? value.trim() : "");
        // 更新 NL 摘要
        Map<String, String> updated = getAllSlotValues(sessionId);
        updateNlSummary(sessionId, updated);

        // 推进到下一个未填的必填槽位
        int currentIndex = findSlotIndex(slotName);
        int nextIndex = currentIndex + 1;
        while (nextIndex < slotDefinitions.size() && !slotDefinitions.get(nextIndex).isRequired()) {
            nextIndex++;
        }

        if (nextIndex >= slotDefinitions.size()) {
            // 所有必填完成 — 尝试可选槽位
            int optionalIndex = findFirstUnfilledOptional(sessionId);
            if (optionalIndex >= 0) {
                setCurrentSlot(sessionId, optionalIndex);
                return FillingResult.nextSlot(slotDefinitions.get(optionalIndex).getPromptTemplate());
            }
            return completeFilling(sessionId);
        }

        setCurrentSlot(sessionId, nextIndex);
        int round = getFillRound(sessionId) + 1;
        if (round > MAX_FILL_ROUNDS) {
            return FillingResult.redirectToManual(
                    Map.copyOf(getAllSlotValues(sessionId)),
                    "对话已超过" + MAX_FILL_ROUNDS + "轮，建议您转到页面继续填写。");
        }
        setFillRound(sessionId, round);
        String nextPrompt = "已记录" + slotDefinitions.get(currentIndex).getLabel()
                + "。" + slotDefinitions.get(nextIndex).getPromptTemplate();
        return FillingResult.nextSlot(nextPrompt);
    }

    /** 修改槽位 → 回退 */
    private FillingResult handleModify(String sessionId, SlotExtractionResult extraction) {
        String targetSlot = extraction.targetSlot();
        if (targetSlot == null) {
            return FillingResult.nextSlot("请问您要修改哪个信息？（联系人、手机号、投诉类型、问题描述）");
        }

        // 找到目标槽位索引
        int idx = findSlotIndex(targetSlot);
        if (idx < 0) {
            return FillingResult.nextSlot("未找到可修改的字段：" + targetSlot);
        }

        // 如果用户直接给了新值，同时写入
        if (extraction.extractedValue() != null && !extraction.extractedValue().isBlank()) {
            setSlotValue(sessionId, targetSlot, extraction.extractedValue().trim());
            updateNlSummary(sessionId, getAllSlotValues(sessionId));
            // 继续当前槽位
            setCurrentSlot(sessionId, idx + 1 >= slotDefinitions.size() ? idx : idx + 1);
            String next = idx + 1 < slotDefinitions.size()
                    ? slotDefinitions.get(idx + 1).getPromptTemplate()
                    : "已更新。请回复「提交」创建工单。";
            return FillingResult.nextSlot("已将" + slotDefinitions.get(idx).getLabel()
                    + "更新为 " + extraction.extractedValue() + "。" + next);
        }

        // 用户没说新值 — 回退到该槽位，重新询问
        setCurrentSlot(sessionId, idx);
        return FillingResult.nextSlot("请提供新的" + slotDefinitions.get(idx).getLabel() + "：");
    }

    /** 闲聊 — 挂起槽位，返回中断信号 */
    private FillingResult handleChitchat(String sessionId) {
        setInterruption(sessionId, true, 1, getCurrentSlot(sessionId));
        return FillingResult.interrupted("chitchat",
                SlotDefinition.builder().name("chitchat").label("闲聊").required(false)
                        .promptTemplate("好的，我理解。").build());
    }

    /** 新意图 — 挂起槽位，返回中断信号让调用方路由 */
    private FillingResult handleNewIntent(String sessionId, SlotExtractionResult extraction) {
        setInterruption(sessionId, true, 1, getCurrentSlot(sessionId));
        return FillingResult.interrupted(extraction.reasoning() != null
                ? extraction.reasoning() : "new_intent",
                getCurrentSlotDef(sessionId));
    }

    /** 槽位填充完成 */
    private FillingResult completeFilling(String sessionId) {
        Map<String, String> allValues = getAllSlotValues(sessionId);
        sessionManager.transition(sessionId, SessionStatus.TICKET_SUBMIT);
        clearInterruption(sessionId);
        log.info("Slot-Filling 完成, sessionId={}, values={}", sessionId, allValues);
        return FillingResult.allDone(allValues);
    }

    // ==================== NL 摘要 ====================

    private String getNlSummary(String sessionId) {
        String s = (String) redisTemplate.opsForHash().get(SESSION_PREFIX + sessionId, FIELD_NL_SUMMARY);
        return s != null ? s : "";
    }

    private void updateNlSummary(String sessionId, Map<String, String> slots) {
        StringBuilder sb = new StringBuilder();
        List<String> filled = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (SlotDefinition def : slotDefinitions) {
            String v = slots.get(def.getName());
            if (v != null && !v.isBlank()) {
                String display = "contactPhone".equals(def.getName()) ? maskPhone(v) : v;
                filled.add(def.getLabel() + "：" + display);
            } else if (def.isRequired()) {
                missing.add(def.getLabel());
            }
        }
        if (!filled.isEmpty()) sb.append("已填：").append(String.join("，", filled)).append("。");
        if (!missing.isEmpty()) sb.append("待填：").append(String.join("、", missing)).append("。");
        redisTemplate.opsForHash().put(SESSION_PREFIX + sessionId, FIELD_NL_SUMMARY, sb.toString());
    }

    private void clearNlSummary(String sessionId) {
        redisTemplate.opsForHash().delete(SESSION_PREFIX + sessionId, FIELD_NL_SUMMARY);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    // ==================== 中断状态 ====================

    private void setInterruption(String sessionId, boolean active, int rounds, int resumeSlot) {
        try {
            Map<String, Object> m = Map.of("active", active, "rounds", rounds, "resumeSlot", resumeSlot);
            redisTemplate.opsForHash().put(SESSION_PREFIX + sessionId, FIELD_INTERRUPTION,
                    objectMapper.writeValueAsString(m));
        } catch (JsonProcessingException e) {
            log.error("中断状态序列化失败", e);
        }
    }

    private void clearInterruption(String sessionId) {
        redisTemplate.opsForHash().delete(SESSION_PREFIX + sessionId, FIELD_INTERRUPTION);
    }

    /** 判断用户消息是否像是在闲聊/追问（而非回到槽位填写） */
    private boolean isChitchatFollowup(String userMessage) {
        String m = userMessage.trim();
        // 短回复 + 不在提交/取消列表 → 可能是槽位回答
        if (m.length() <= 20 && !SUBMIT_PATTERN.matcher(m).matches()) {
            return false; // 像槽位回答
        }
        // 包含政策/法律关键词 → 像新意图追问
        return m.contains("？") || m.contains("?") || m.contains("法律") || m.contains("违法");
    }

    // ==================== 辅助方法 ====================

    private SlotDefinition getCurrentSlotDef(String sessionId) {
        int idx = getCurrentSlot(sessionId);
        if (idx < 0 || idx >= slotDefinitions.size()) return null;
        return slotDefinitions.get(idx);
    }

    private int findSlotIndex(String slotName) {
        for (int i = 0; i < slotDefinitions.size(); i++) {
            if (slotDefinitions.get(i).getName().equals(slotName)) return i;
        }
        return -1;
    }

    private boolean allRequiredFilled(Map<String, String> slots) {
        return slotDefinitions.stream()
                .filter(SlotDefinition::isRequired)
                .allMatch(d -> slots.get(d.getName()) != null && !slots.get(d.getName()).isBlank());
    }

    private int findFirstUnfilledOptional(String sessionId) {
        Map<String, String> slots = getAllSlotValues(sessionId);
        for (int i = 0; i < slotDefinitions.size(); i++) {
            SlotDefinition def = slotDefinitions.get(i);
            if (!def.isRequired() && !slots.containsKey(def.getName())) return i;
        }
        return -1;
    }

    // ==================== Redis 读写 ====================

    private void clearSlots(String sessionId) {
        redisTemplate.opsForHash().delete(SESSION_PREFIX + sessionId,
                FIELD_SLOTS, FIELD_CURRENT_SLOT, FIELD_FILL_ROUND);
    }

    private void setSlotValue(String sessionId, String slotName, String value) {
        Map<String, String> slots = new HashMap<>(getAllSlotValues(sessionId));
        slots.put(slotName, value);
        try {
            redisTemplate.opsForHash().put(SESSION_PREFIX + sessionId, FIELD_SLOTS,
                    objectMapper.writeValueAsString(slots));
        } catch (JsonProcessingException e) {
            log.error("槽位值序列化失败", e);
        }
    }

    private int getCurrentSlot(String sessionId) {
        String val = (String) redisTemplate.opsForHash().get(SESSION_PREFIX + sessionId, FIELD_CURRENT_SLOT);
        return val != null ? Integer.parseInt(val) : -1;
    }

    private void setCurrentSlot(String sessionId, int index) {
        redisTemplate.opsForHash().put(SESSION_PREFIX + sessionId, FIELD_CURRENT_SLOT, String.valueOf(index));
    }

    private int getFillRound(String sessionId) {
        String val = (String) redisTemplate.opsForHash().get(SESSION_PREFIX + sessionId, FIELD_FILL_ROUND);
        return val != null ? Integer.parseInt(val) : 0;
    }

    private void setFillRound(String sessionId, int round) {
        redisTemplate.opsForHash().put(SESSION_PREFIX + sessionId, FIELD_FILL_ROUND, String.valueOf(round));
    }

    // ===== Inner types =====

    public record FillingResult(
            boolean done,
            String nextPrompt,
            String retrySlot,
            String retryMessage,
            Map<String, String> slotValues,
            /** fill | modify | chitchat | new_intent | cancel */
            String resultType,
            /** 中断原因/LLM reasoning — 供调用方路由判断 */
            String interruptReason,
            /** 中断时的槽位定义 — 供调用方了解上下文 */
            SlotDefinition interruptedSlot
    ) {

        // ---- 工厂方法 ----

        public static FillingResult nextSlot(String prompt) {
            return new FillingResult(false, prompt, null, null, null, "fill", null, null);
        }

        public static FillingResult retry(String slotName, String message) {
            return new FillingResult(false, null, slotName, message, null, "retry", null, null);
        }

        public static FillingResult allDone(Map<String, String> values) {
            return new FillingResult(true, null, null, null, values, "done", null, null);
        }

        public static FillingResult timeout(int maxRounds) {
            return new FillingResult(true,
                    "信息采集超时（已超过" + maxRounds + "轮），您可以通过人工客服提交",
                    null, null, null, "timeout", null, null);
        }

        public static FillingResult cancelled() {
            return new FillingResult(true,
                    "好的，已取消本次工单填写。如有需要随时找我。",
                    null, null, null, "cancel", null, null);
        }

        /** 中断：闲聊或新意图 — 调用方需回答用户消息后决定是否回归槽位 */
        public static FillingResult interrupted(String reason, SlotDefinition interruptedSlot) {
            return new FillingResult(false, null, null, null, null,
                    "interrupted", reason, interruptedSlot);
        }

        /** 引导用户转手工填写 — 超轮次/连败/主动要求 */
        public static FillingResult redirectToManual(Map<String, String> prefilledSlots, String message) {
            return new FillingResult(true, message, null, null,
                    prefilledSlots, "redirect", null, null);
        }

        // ---- 便捷判断 ----

        public boolean needsRetry() { return retrySlot != null; }
        public boolean isInterrupted() { return "interrupted".equals(resultType); }
        public boolean isCancelled() { return "cancel".equals(resultType); }
        public boolean isDone() { return done && "done".equals(resultType); }
        public boolean isRedirect() { return "redirect".equals(resultType); }
    }
}
