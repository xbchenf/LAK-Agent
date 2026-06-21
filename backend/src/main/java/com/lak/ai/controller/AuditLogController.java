package com.lak.ai.controller;

import com.lak.ai.common.response.ApiResponse;
import com.lak.ai.common.response.PageResult;
import com.lak.ai.common.security.MenuPermissionChecker;
import com.lak.ai.model.dto.AuditLogQueryDTO;
import com.lak.ai.model.vo.AuditLogVO;
import com.lak.ai.service.audit.AuditLogQueryService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 操作审计接口 — 审计日志查询（只读）。
 * <p>
 * 所有端点需 JWT 认证 + audit 菜单权限。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogQueryService auditLogQueryService;
    private final MenuPermissionChecker menuPermissionChecker;

    /** 分页查询审计日志 */
    @GetMapping("/audit-logs")
    public ApiResponse<PageResult<AuditLogVO>> list(
            @ModelAttribute AuditLogQueryDTO query,
            HttpServletRequest request) {
        menuPermissionChecker.requireMenu(request, "audit");
        PageResult<AuditLogVO> result = auditLogQueryService.list(query);
        return ApiResponse.success(result);
    }

    /** 查看审计日志详情（支持跨月查询） */
    @GetMapping("/audit-logs/{id}")
    public ApiResponse<AuditLogVO> detail(
            @PathVariable Long id,
            @RequestParam(required = false) String month,
            HttpServletRequest request) {
        menuPermissionChecker.requireMenu(request, "audit");
        AuditLogVO vo = auditLogQueryService.getById(id, month);
        if (vo == null) {
            return ApiResponse.error(404, "审计记录不存在");
        }
        return ApiResponse.success(vo);
    }
}
