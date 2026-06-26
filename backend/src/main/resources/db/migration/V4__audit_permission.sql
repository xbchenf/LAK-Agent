-- =====================================================
-- V4: 操作审计菜单权限补充
-- =====================================================
-- 审计菜单在 V3 中已有 sys_menu 记录(id=34, menu_code='audit')
-- 本迁移补充对应的 sys_permission + sys_menu_permission 记录
-- =====================================================

-- ---- 审计日志权限 ----
INSERT INTO sys_permission (perm_code, perm_name, resource_path, method) VALUES
  ('admin:audit:list',   '审计-列表',   '/api/v1/admin/audit-logs',      'GET'),
  ('admin:audit:detail', '审计-详情',   '/api/v1/admin/audit-logs/{id}', 'GET')
ON DUPLICATE KEY UPDATE perm_name = VALUES(perm_name);

-- ---- 菜单-权限映射（audit 菜单 → 审计权限） ----
INSERT INTO sys_menu_permission (menu_id, permission_id)
SELECT m.id, p.id FROM sys_menu m, sys_permission p
WHERE m.menu_code = 'audit' AND p.perm_code LIKE 'admin:audit:%'
ON DUPLICATE KEY UPDATE menu_id = VALUES(menu_id);
