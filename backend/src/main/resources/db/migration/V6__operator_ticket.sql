-- =============================================================================
-- V6 — 人工坐席工单处理 + 坐席菜单
-- =============================================================================

-- ---- 1. ticket 表新增坐席处理字段 ----
ALTER TABLE `ticket`
    ADD COLUMN `assignee_id`   BIGINT       DEFAULT NULL COMMENT '坐席用户ID（sys_user.id）' AFTER `external_ticket_id`,
    ADD COLUMN `assigned_at`   DATETIME     DEFAULT NULL COMMENT '接单时间' AFTER `assignee_id`,
    ADD COLUMN `handled_at`    DATETIME     DEFAULT NULL COMMENT '处理完成时间' AFTER `assigned_at`,
    ADD COLUMN `handler_notes` TEXT         DEFAULT NULL COMMENT '坐席处理意见' AFTER `handled_at`,
    ADD COLUMN `priority`      VARCHAR(16)  DEFAULT 'NORMAL' COMMENT '优先级: NORMAL/URGENT/LOW' AFTER `handler_notes`;

-- ---- 2. 坐席菜单 ----
INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, icon, sort_order) VALUES
    (4, NULL, 'operator', '坐席工作台', NULL, 4)
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name);

-- ---- 3. 坐席 API 权限 ----
INSERT INTO sys_permission (perm_code, perm_name, resource_path, method) VALUES
    ('operator:ticket:pending', '坐席-待处理列表', '/api/v1/operator/tickets/pending', 'GET'),
    ('operator:ticket:claim',   '坐席-认领工单',   '/api/v1/operator/tickets/{ticketNo}/claim', 'POST'),
    ('operator:ticket:process', '坐席-处理工单',   '/api/v1/operator/tickets/{ticketNo}', 'PUT'),
    ('operator:ticket:detail',  '坐席-工单详情',   '/api/v1/operator/tickets/{ticketNo}', 'GET')
ON DUPLICATE KEY UPDATE perm_name = VALUES(perm_name);

-- ---- 4. 菜单-权限映射 ----
INSERT INTO sys_menu_permission (menu_id, permission_id)
SELECT m.id, p.id FROM sys_menu m, sys_permission p
WHERE m.menu_code = 'operator' AND p.perm_code LIKE 'operator:ticket:%'
ON DUPLICATE KEY UPDATE menu_id = VALUES(menu_id);

-- ---- 5. ADMIN 角色自动获得坐席菜单 ----
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE menu_code = 'operator'
ON DUPLICATE KEY UPDATE menu_id = VALUES(menu_id);

-- ---- 6. OPERATOR 角色获得坐席菜单 ----
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, id FROM sys_menu WHERE menu_code = 'operator'
ON DUPLICATE KEY UPDATE menu_id = VALUES(menu_id);
