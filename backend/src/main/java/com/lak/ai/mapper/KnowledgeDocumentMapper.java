package com.lak.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lak.ai.model.entity.KnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 知识文档 Mapper。
 * <p>
 * 继承 Mybatis-Plus BaseMapper 获得通用 CRUD，自定义复杂查询。
 */
@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {

    /**
     * 按 doc_id 唯一查询。
     */
    @Select("SELECT * FROM knowledge_document WHERE doc_id = #{docId}")
    KnowledgeDocument selectByDocId(@Param("docId") String docId);

    /**
     * 分页查询 — 支持类型/状态/关键词筛选。
     * <p>
     * 使用 Mybatis-Plus 分页插件 + 动态 SQL。
     */
    default IPage<KnowledgeDocument> selectPageWithFilters(
            Page<KnowledgeDocument> page, String docType, String status, String keyword) {
        return selectPage(page, new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgeDocument>()
                .eq(docType != null && !docType.isEmpty(), KnowledgeDocument::getDocType, docType)
                .eq(status != null && !status.isEmpty(), KnowledgeDocument::getStatus, status)
                .like(keyword != null && !keyword.isEmpty(), KnowledgeDocument::getTitle, keyword)
                .orderByDesc(KnowledgeDocument::getCreateTime)
        );
    }
}
