package com.lak.ai.controller;

import com.lak.ai.common.response.ApiResponse;
import com.lak.ai.common.response.PageResult;
import com.lak.ai.common.security.MenuPermissionChecker;
import com.lak.ai.model.dto.DocumentQueryDTO;
import com.lak.ai.model.dto.StatusActionDTO;
import com.lak.ai.model.vo.DocumentChunkVO;
import com.lak.ai.model.vo.DocumentVO;
import com.lak.ai.service.knowledge.KnowledgeDocumentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * 知识库管理接口。
 * <p>
 * 所有端点需 JWT 认证 + knowledge 菜单权限。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeDocumentService knowledgeDocumentService;
    private final MenuPermissionChecker menuPermissionChecker;

    // ===== POST /documents — 上传文档 =====

    @PostMapping("/documents")
    public ApiResponse<DocumentVO> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("docType") String docType,
            @RequestParam(value = "effectiveDate", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate effectiveDate,
            @RequestParam(value = "expireDate", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate expireDate,
            HttpServletRequest request) {
        menuPermissionChecker.requireMenu(request, "knowledge");
        log.info("上传文档, filename={}, docType={}, size={}", file.getOriginalFilename(), docType, file.getSize());
        DocumentVO vo = knowledgeDocumentService.upload(file, docType, effectiveDate, expireDate);
        return ApiResponse.success(vo, "上传成功");
    }

    // ===== GET /documents — 分页列表 =====

    @GetMapping("/documents")
    public ApiResponse<PageResult<DocumentVO>> list(
            @ModelAttribute DocumentQueryDTO query,
            HttpServletRequest request) {
        menuPermissionChecker.requireMenu(request, "knowledge");
        PageResult<DocumentVO> result = knowledgeDocumentService.list(query);
        return ApiResponse.success(result);
    }

    // ===== GET /documents/{docId} — 文档详情 =====

    @GetMapping("/documents/{docId}")
    public ApiResponse<DocumentVO> get(
            @PathVariable String docId,
            HttpServletRequest request) {
        menuPermissionChecker.requireMenu(request, "knowledge");
        DocumentVO vo = knowledgeDocumentService.getByDocId(docId);
        return ApiResponse.success(vo);
    }

    // ===== PUT /documents/{docId} — 编辑元信息 =====

    @PutMapping("/documents/{docId}")
    public ApiResponse<DocumentVO> update(
            @PathVariable String docId,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "effectiveDate", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate effectiveDate,
            @RequestParam(value = "expireDate", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate expireDate,
            HttpServletRequest request) {
        menuPermissionChecker.requireMenu(request, "knowledge");
        DocumentVO vo = knowledgeDocumentService.update(docId, title, effectiveDate, expireDate);
        return ApiResponse.success(vo, "更新成功");
    }

    // ===== PATCH /documents/{docId}/status — 状态变更 =====

    @PatchMapping("/documents/{docId}/status")
    public ApiResponse<DocumentVO> changeStatus(
            @PathVariable String docId,
            @RequestBody StatusActionDTO action,
            HttpServletRequest request) {
        menuPermissionChecker.requireMenu(request, "knowledge");
        log.info("状态变更, docId={}, action={}", docId, action.getAction());
        DocumentVO vo = knowledgeDocumentService.changeStatus(docId, action);
        return ApiResponse.success(vo, "操作成功");
    }

    // ===== DELETE /documents/{docId} — 删除文档 =====

    @DeleteMapping("/documents/{docId}")
    public ApiResponse<Void> delete(
            @PathVariable String docId,
            HttpServletRequest request) {
        menuPermissionChecker.requireMenu(request, "knowledge");
        log.info("删除文档, docId={}", docId);
        knowledgeDocumentService.delete(docId);
        return ApiResponse.success(null, "删除成功");
    }

    // ===== GET /documents/{docId}/chunks — 分块详情 =====

    @GetMapping("/documents/{docId}/chunks")
    public ApiResponse<List<DocumentChunkVO>> chunks(
            @PathVariable String docId,
            HttpServletRequest request) {
        menuPermissionChecker.requireMenu(request, "knowledge");
        List<DocumentChunkVO> chunks = knowledgeDocumentService.getChunks(docId);
        return ApiResponse.success(chunks);
    }

    // ===== POST /documents/{docId}/reindex — 重新索引 =====

    @PostMapping("/documents/{docId}/reindex")
    public ApiResponse<DocumentVO> reindex(
            @PathVariable String docId,
            HttpServletRequest request) {
        menuPermissionChecker.requireMenu(request, "knowledge");
        log.info("重新索引, docId={}", docId);
        DocumentVO vo = knowledgeDocumentService.reindex(docId);
        return ApiResponse.success(vo, "重新索引完成");
    }
}
