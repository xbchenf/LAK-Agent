-- =============================================================================
-- LAK-Agent — V2 初始化种子数据
-- =============================================================================

-- ===== 角色 =====
INSERT INTO `sys_role` (`role_code`, `role_name`, `description`) VALUES
('ADMIN', '系统管理员', '拥有全部管理权限'),
('OPERATOR', '运营人员', '知识库管理、工单处理'),
('USER', '普通用户', '对话问答、工单提交');

-- ===== 管理员用户 (密码: admin123，BCrypt加密) =====
-- ⚠️ 生产环境请立即修改密码
INSERT INTO `sys_user` (`username`, `password`, `real_name`, `role_id`, `status`) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '系统管理员', 1, 'ACTIVE');

-- ===== 权限 =====
INSERT INTO `sys_permission` (`perm_code`, `perm_name`, `resource_path`, `method`) VALUES
-- 对话权限
('chat:send',    '发送消息',     '/api/v1/chat/message',           'POST'),
('chat:list',    '会话列表',     '/api/v1/chat/sessions',          'GET'),
('chat:detail',  '会话详情',     '/api/v1/chat/sessions/{id}',     'GET'),
('chat:delete',  '删除会话',     '/api/v1/chat/sessions/{id}',     'DELETE'),
-- 工单权限
('ticket:create','创建工单',     '/api/v1/tickets',                'POST'),
('ticket:query', '查询工单',     '/api/v1/tickets/{ticketNo}',     'GET'),
-- 管理权限
('admin:sensitive-words', '敏感词管理', '/api/v1/admin/sensitive-words/reload', 'POST');

-- ===== 角色-权限关联 =====
-- ADMIN 拥有全部权限
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT (SELECT id FROM sys_role WHERE role_code='ADMIN'), id FROM sys_permission;

-- USER 拥有对话+工单权限
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT (SELECT id FROM sys_role WHERE role_code='USER'), id FROM sys_permission
WHERE perm_code IN ('chat:send','chat:list','chat:detail','chat:delete','ticket:create','ticket:query');

-- ===== 敏感词示例文件（占位）=====
-- 实际敏感词库应通过部署时挂载 /config/sensitive-words.txt 提供
