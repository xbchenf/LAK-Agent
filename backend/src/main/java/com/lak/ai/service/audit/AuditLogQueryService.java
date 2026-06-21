package com.lak.ai.service.audit;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lak.ai.common.response.PageResult;
import com.lak.ai.config.MybatisPlusConfig;
import com.lak.ai.mapper.AuditLogMapper;
import com.lak.ai.mapper.SysUserMapper;
import com.lak.ai.model.dto.AuditLogQueryDTO;
import com.lak.ai.model.entity.AuditLog;
import com.lak.ai.model.entity.SysUser;
import com.lak.ai.model.vo.AuditLogVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.sql.SQLSyntaxErrorException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 审计日志查询服务 — 分页查询 + 用户名解析 + 操作类型映射。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogQueryService {

    private final AuditLogMapper auditLogMapper;
    private final SysUserMapper sysUserMapper;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** 获取默认月份（当前月 yyyyMM） */
    public static String currentMonth() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
    }

    /**
     * 分页查询审计日志。
     */
    /** 月份格式：严格6位数字 yyyyMM */
    private static final java.util.regex.Pattern MONTH_PATTERN =
            java.util.regex.Pattern.compile("^\\d{6}$");

    public PageResult<AuditLogVO> list(AuditLogQueryDTO query) {
        String month = query.getMonth() != null ? query.getMonth() : currentMonth();
        if (!MONTH_PATTERN.matcher(month).matches()) {
            throw new IllegalArgumentException("月份格式错误，需为 yyyyMM（6位数字）");
        }
        int pageNum = query.getPage() != null ? query.getPage() : 1;
        int pageSize = query.getSize() != null ? query.getSize() : 20;

        LocalDateTime startTime = parseDateTime(query.getStartDate(), true);
        LocalDateTime endTime = parseDateTime(query.getEndDate(), false);

        try {
            MybatisPlusConfig.setAuditMonth(month);
            Page<AuditLog> page = auditLogMapper.selectPageWithFilters(
                    new Page<>(pageNum, pageSize),
                    query.getUserId(), query.getStatus(), query.getKeyword(),
                    startTime, endTime);

            Map<Long, String> userMap = resolveUsernames(page.getRecords());

            List<AuditLogVO> vos = page.getRecords().stream()
                    .map(log -> toVO(log, userMap))
                    .collect(Collectors.toList());

            return PageResult.of(vos, page.getTotal(), pageNum, pageSize);
        } catch (DataAccessException e) {
            // 月份表不存在时返回空结果（如202605表尚未创建）
            log.warn("审计日志查询失败, month={}, error={}", month, e.getMessage());
            return PageResult.empty(pageNum, pageSize);
        } finally {
            MybatisPlusConfig.clearAuditMonth();
        }
    }

    /**
     * 查询单条审计日志详情。
     * @param id 主键ID
     * @param month 目标月份（yyyyMM），默认当前月
     */
    public AuditLogVO getById(Long id, String month) {
        if (month == null || month.isBlank()) {
            month = currentMonth();
        }
        if (!MONTH_PATTERN.matcher(month).matches()) {
            throw new IllegalArgumentException("月份格式错误，需为 yyyyMM（6位数字）");
        }
        try {
            MybatisPlusConfig.setAuditMonth(month);
            AuditLog log = auditLogMapper.selectById(id);
            if (log == null) return null;
            Map<Long, String> userMap = resolveUsernames(List.of(log));
            return toVO(log, userMap);
        } catch (DataAccessException e) {
            log.warn("审计详情查询失败, id={}, month={}, error={}", id, month, e.getMessage());
            return null;
        } finally {
            MybatisPlusConfig.clearAuditMonth();
        }
    }

    // ===== 私有方法 =====

    /** Entity → VO，含 requestUri 解析和操作类型映射 */
    private AuditLogVO toVO(AuditLog log, Map<Long, String> userMap) {
        AuditLogVO vo = new AuditLogVO();
        vo.setId(log.getId());
        vo.setTraceId(log.getTraceId());
        vo.setSessionId(log.getSessionId());
        vo.setUserId(log.getUserId());
        vo.setUsername(log.getUserId() != null ? userMap.getOrDefault(log.getUserId(), "--") : "--");
        // 从 request_body JSON 提取 uri
        String uri = extractUri(log.getRequestBody());
        vo.setRequestUri(uri);
        vo.setOperation(uriToLabel(uri));
        vo.setRequestBody(log.getRequestBody());
        vo.setResponseBody(log.getResponseBody());
        vo.setIntentType(log.getIntentType());
        vo.setConfidence(log.getConfidence());
        vo.setModelParams(log.getModelParams());
        vo.setModelResponse(log.getModelResponse());
        vo.setRetrievalFragments(log.getRetrievalFragments());
        vo.setLatencyMs(log.getLatencyMs());
        vo.setStatus(log.getStatus());
        vo.setErrorMessage(log.getErrorMessage());
        vo.setCreateTime(log.getCreateTime());
        return vo;
    }

    /** 从 request_body JSON 中提取 uri */
    private String extractUri(String requestBody) {
        if (requestBody == null || requestBody.isBlank()) return null;
        try {
            JsonNode node = objectMapper.readTree(requestBody);
            return node.has("uri") ? node.get("uri").asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** URI → 操作类型中文名 */
    private String uriToLabel(String uri) {
        if (uri == null) return "--";
        if (uri.contains("/chat")) return "智能问答";
        if (uri.contains("/tickets")) return "投诉建议";
        if (uri.contains("/knowledge")) return "知识库管理";
        if (uri.contains("/admin/users")) return "用户管理";
        if (uri.contains("/admin/roles") || uri.contains("/admin/menus")) return "角色管理";
        if (uri.contains("/admin/sensitive-words")) return "敏感词管理";
        if (uri.contains("/admin/audit-logs")) return "操作审计";
        if (uri.contains("/auth/")) return "认证登录";
        return uri; // fallback: 直接显示 URI
    }

    /** 批量查询用户名 */
    private Map<Long, String> resolveUsernames(List<AuditLog> logs) {
        Set<Long> userIds = logs.stream()
                .map(AuditLog::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) return Map.of();
        List<SysUser> users = sysUserMapper.selectBatchIds(userIds);
        return users.stream()
                .collect(Collectors.toMap(SysUser::getId, u ->
                        u.getRealName() != null ? u.getRealName() : u.getUsername(),
                        (a, b) -> a));
    }

    /** 解析日期字符串为 LocalDateTime */
    private LocalDateTime parseDateTime(String dateStr, boolean startOfDay) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
            return startOfDay ? date.atStartOfDay() : date.atTime(23, 59, 59);
        } catch (Exception e) {
            return null;
        }
    }
}
