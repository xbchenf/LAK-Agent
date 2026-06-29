-- =============================================================================
-- V7 — 敏感词管理权限补充
-- =============================================================================

INSERT INTO sys_permission (perm_code, perm_name, resource_path, method) VALUES
    ('admin:sensitive-words:list', '敏感词-列表', '/api/v1/admin/sensitive-words/list', 'GET')
ON DUPLICATE KEY UPDATE perm_name = VALUES(perm_name);

-- 将新权限关联到敏感词菜单
INSERT INTO sys_menu_permission (menu_id, permission_id)
SELECT m.id, p.id FROM sys_menu m, sys_permission p
WHERE m.menu_code = 'sensitive' AND p.perm_code = 'admin:sensitive-words:list'
ON DUPLICATE KEY UPDATE menu_id = VALUES(menu_id);
