-- =============================================================================
-- LAK-Agent — V1 初始化数据库结构
-- 数据库: lak_ai_platform | 字符集: utf8mb4 | 排序: utf8mb4_unicode_ci
-- 遵循阿里巴巴Java开发手册 — 表名小写蛇形, 主键 bigint id, 索引名 uk_/idx_ 前缀
-- =============================================================================

-- ===== 用户与权限 =====

CREATE TABLE IF NOT EXISTS `sys_user` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `username`   VARCHAR(64)  NOT NULL COMMENT '用户名',
    `password`   VARCHAR(256) NOT NULL COMMENT '密码（BCrypt）',
    `real_name`  VARCHAR(64)  DEFAULT NULL COMMENT '真实姓名',
    `email`      VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    `phone`      VARCHAR(32)  DEFAULT NULL COMMENT '手机号（AES-256加密存储）',
    `status`     VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED/LOCKED',
    `role_id`    BIGINT       DEFAULT NULL COMMENT '关联角色',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`) USING BTREE COMMENT '用户名唯一索引',
    KEY `idx_role_id` (`role_id`) USING BTREE COMMENT '按角色查询用户',
    KEY `idx_status` (`status`) USING BTREE COMMENT '按状态过滤用户'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

CREATE TABLE IF NOT EXISTS `sys_role` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `role_code`   VARCHAR(64)  NOT NULL COMMENT '角色编码',
    `role_name`   VARCHAR(128) NOT NULL COMMENT '角色名称',
    `description` VARCHAR(512) DEFAULT NULL COMMENT '角色描述',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`) USING BTREE COMMENT '角色编码唯一索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色表';

CREATE TABLE IF NOT EXISTS `sys_permission` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `perm_code`     VARCHAR(128) NOT NULL COMMENT '权限编码',
    `perm_name`     VARCHAR(128) NOT NULL COMMENT '权限名称',
    `resource_path` VARCHAR(256) DEFAULT NULL COMMENT '资源路径（Ant风格）',
    `method`        VARCHAR(16)  DEFAULT NULL COMMENT 'HTTP方法（GET/POST/PUT/DELETE/*）',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_perm_code` (`perm_code`) USING BTREE COMMENT '权限编码唯一索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统权限表';

CREATE TABLE IF NOT EXISTS `sys_role_permission` (
    `role_id`       BIGINT NOT NULL COMMENT '角色ID',
    `permission_id` BIGINT NOT NULL COMMENT '权限ID',
    PRIMARY KEY (`role_id`, `permission_id`),
    KEY `idx_permission_id` (`permission_id`) USING BTREE COMMENT '反向查询——按权限查角色'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-权限关联表';

-- ===== 核心业务表 =====

CREATE TABLE IF NOT EXISTS `chat_session` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `session_id`  VARCHAR(64)  NOT NULL COMMENT '会话UUID',
    `user_id`     BIGINT       DEFAULT NULL COMMENT '关联用户ID',
    `status`      VARCHAR(32)  NOT NULL DEFAULT 'NEW' COMMENT 'NEW/INTENT_CHECK/ANSWERING/COLLECT_INFO/FALLBACK/COMPLIANCE_CHECK/TICKET_SUBMIT/CLOSED',
    `intent_type` VARCHAR(32)  DEFAULT NULL COMMENT 'POLICY_CONSULT/PROCEDURE_GUIDE/COMPLAINT_SUGGEST/CHITCHAT/UNKNOWN',
    `confidence`  DECIMAL(3,2) DEFAULT NULL COMMENT '意图识别置信度(0.00-1.00)',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_id` (`session_id`) USING BTREE COMMENT '会话ID唯一索引',
    KEY `idx_user_id` (`user_id`) USING BTREE COMMENT '按用户查询会话列表',
    KEY `idx_create_time` (`create_time`) USING BTREE COMMENT '按时间范围查询和历史归档'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话会话表';

CREATE TABLE IF NOT EXISTS `chat_message` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `session_id`  VARCHAR(64)  NOT NULL COMMENT '关联会话UUID',
    `role`        VARCHAR(16)  NOT NULL COMMENT 'user/assistant/system',
    `content`     TEXT         NOT NULL COMMENT '消息内容',
    `tokens`      INT          DEFAULT NULL COMMENT 'Token消耗',
    `source_docs` JSON         DEFAULT NULL COMMENT '溯源文档列表(JSON数组)',
    `confidence`  DECIMAL(3,2) DEFAULT NULL COMMENT '答复置信度(0.00-1.00)',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_session_id` (`session_id`) USING BTREE COMMENT '按会话查询消息列表',
    KEY `idx_create_time` (`create_time`) USING BTREE COMMENT '按时间范围查询消息'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息记录表';

CREATE TABLE IF NOT EXISTS `ticket` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `ticket_no`          VARCHAR(32)  NOT NULL COMMENT '工单编号',
    `session_id`         VARCHAR(64)  NOT NULL COMMENT '关联会话UUID',
    `complaint_type`     VARCHAR(32)  NOT NULL COMMENT 'LAW_ENFORCEMENT/SERVICE_COMPLAINT/DISCIPLINE_REPORT/OTHER',
    `contact_name`       VARCHAR(64)  NOT NULL COMMENT '联系人姓名',
    `contact_phone`      VARCHAR(256) NOT NULL COMMENT '联系电话（AES-256加密）',
    `description`        TEXT         NOT NULL COMMENT '问题描述',
    `attachment_url`     VARCHAR(512) DEFAULT NULL COMMENT '附件URL',
    `status`             VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/COMPLETED/FAILED',
    `external_ticket_id` VARCHAR(64)  DEFAULT NULL COMMENT '外部工单系统ID',
    `create_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ticket_no` (`ticket_no`) USING BTREE COMMENT '工单编号唯一索引',
    UNIQUE KEY `uk_session_id` (`session_id`) USING BTREE COMMENT '与会话1:1关联',
    KEY `idx_status` (`status`) USING BTREE COMMENT '按状态查询工单',
    KEY `idx_create_time` (`create_time`) USING BTREE COMMENT '按时间范围查询工单'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工单表';

CREATE TABLE IF NOT EXISTS `knowledge_document` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `doc_id`           VARCHAR(64)  NOT NULL COMMENT '文档唯一标识',
    `title`            VARCHAR(256) NOT NULL COMMENT '文档标题',
    `doc_type`         VARCHAR(32)  NOT NULL COMMENT 'POLICY/PROCEDURE/TEMPLATE',
    `file_url`         VARCHAR(512) DEFAULT NULL COMMENT '文件存储路径',
    `effective_date`   DATE         DEFAULT NULL COMMENT '生效日期',
    `expire_date`      DATE         DEFAULT NULL COMMENT '废止日期(NULL=长期有效)',
    `status`           VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/EXPIRED/DRAFT',
    `chunk_count`      INT          DEFAULT 0 COMMENT '分块数量',
    `qdrant_collection` VARCHAR(64) DEFAULT NULL COMMENT '对应Qdrant Collection',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_doc_id` (`doc_id`) USING BTREE COMMENT '文档唯一标识索引',
    KEY `idx_doc_type` (`doc_type`) USING BTREE COMMENT '按文档类型查询',
    KEY `idx_status` (`status`) USING BTREE COMMENT '按文档状态过滤',
    KEY `idx_effective_date` (`effective_date`) USING BTREE COMMENT '按生效日期查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识文档元数据表';

-- ===== 审计日志 — 按月分表 =====
-- 每月1日由定时任务自动创建下月表，格式: audit_log_YYYYMM

CREATE TABLE IF NOT EXISTS `audit_log_202606` (
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `trace_id`             VARCHAR(64)  NOT NULL COMMENT '全链路追踪ID',
    `session_id`           VARCHAR(64)  DEFAULT NULL COMMENT '关联会话UUID',
    `user_id`              BIGINT       DEFAULT NULL COMMENT '用户ID',
    `request_body`         MEDIUMTEXT   COMMENT '请求体',
    `response_body`        MEDIUMTEXT   COMMENT '响应体',
    `intent_type`          VARCHAR(32)  DEFAULT NULL COMMENT '意图类型',
    `confidence`           DECIMAL(3,2) DEFAULT NULL COMMENT '置信度',
    `model_params`         JSON         DEFAULT NULL COMMENT '大模型调用参数',
    `model_response`       MEDIUMTEXT   COMMENT '大模型原始返回',
    `retrieval_fragments`  JSON         DEFAULT NULL COMMENT 'RAG检索召回片段',
    `latency_ms`           INT          DEFAULT NULL COMMENT '总耗时(毫秒)',
    `status`               VARCHAR(16)  NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS/FAIL/FALLBACK',
    `error_message`        TEXT         COMMENT '异常信息',
    `create_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（分区键）',
    PRIMARY KEY (`id`),
    KEY `idx_trace_id` (`trace_id`) USING BTREE COMMENT 'TraceId索引——全链路追踪',
    KEY `idx_session_id` (`session_id`) USING BTREE COMMENT '按会话查询审计轨迹',
    KEY `idx_create_time` (`create_time`) USING BTREE COMMENT '按时间范围查询审计记录',
    KEY `idx_status` (`status`) USING BTREE COMMENT '按结果状态查询异常记录'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志表(2026年6月)';

-- ⚠️ 审计日志安全规则: 业务写入用户仅授权 INSERT + SELECT，禁止授予 UPDATE/DELETE 权限
