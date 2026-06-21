-- =============================================================================
-- V3 — 角色权限模型升级: 引入菜单层
-- 链路: 角色 → 菜单 → 权限(API路径)
-- =============================================================================
--
-- ╔═══════════════════════════════════════════════════════════════════════════╗
-- ║                        操作指导说明                                    ║
-- ╠═══════════════════════════════════════════════════════════════════════════╣
-- ║                                                                       ║
-- ║  维护层级（谁改什么）：                                                  ║
-- ║                                                                       ║
-- ║  ┌─────────────┬──────────────────┬──────────────────────────────────┐║
-- ║  │ 维护方       │ 维护内容          │ 维护方式                         │║
-- ║  ├─────────────┼──────────────────┼──────────────────────────────────┤║
-- ║  │ 开发者       │ sys_menu          │ 新增 Flyway 迁移脚本             │║
-- ║  │（新增页面时）│ sys_permission    │ INSERT INTO sys_menu /           │║
-- ║  │             │ sys_menu_permission│ sys_permission /                 │║
-- ║  │             │（菜单↔权限映射）   │ sys_menu_permission              │║
-- ║  ├─────────────┼──────────────────┼──────────────────────────────────┤║
-- ║  │ 管理员       │ sys_role_menu     │ 后台"系统管理 → 角色管理"页面    │║
-- ║  │（分配权限时）│（角色↔菜单映射）   │ 勾选功能菜单                     │║
-- ║  └─────────────┴──────────────────┴──────────────────────────────────┘║
-- ║                                                                       ║
-- ║  开发者新增接口示例（新增"统计分析"页面 + 2个接口）：                    ║
-- ║                                                                       ║
-- ║  1. INSERT INTO sys_menu (parent_id, menu_code, menu_name, sort_order) ║
-- ║     VALUES (3, 'stats', '统计分析', 5);  -- 挂到系统管理下              ║
-- ║                                                                       ║
-- ║  2. INSERT INTO sys_permission (perm_code, perm_name, resource_path,    ║
-- ║         method) VALUES                                                 ║
-- ║     ('admin:stats:daily', '统计-日报', '/api/v1/admin/stats/daily',    ║
-- ║      'GET'),                                                           ║
-- ║     ('admin:stats:export', '统计-导出', '/api/v1/admin/stats/export',  ║
-- ║      'POST');                                                          ║
-- ║                                                                       ║
-- ║  3. INSERT INTO sys_menu_permission (menu_id, permission_id)           ║
-- ║     SELECT m.id, p.id FROM sys_menu m, sys_permission p                ║
-- ║     WHERE m.menu_code = 'stats'                                        ║
-- ║       AND p.perm_code IN ('admin:stats:daily','admin:stats:export');   ║
-- ║                                                                       ║
-- ║  注意：菜单↔权限映射不在后台维护，必须走数据库迁移脚本。                 ║
-- ║       这样才能保证代码版本与权限配置严格一致。                           ║
-- ║                                                                       ║
-- ╚═══════════════════════════════════════════════════════════════════════════╝

-- ---- 1. 菜单表 ----

-- ---- 1. 菜单表 ----
CREATE TABLE IF NOT EXISTS sys_menu (
    id          BIGINT      AUTO_INCREMENT PRIMARY KEY,
    parent_id   BIGINT      NULL                COMMENT '父菜单ID，NULL=一级菜单',
    menu_code   VARCHAR(32) NOT NULL            COMMENT '菜单编码，唯一',
    menu_name   VARCHAR(64) NOT NULL            COMMENT '菜单名称',
    icon        VARCHAR(32) DEFAULT NULL        COMMENT '图标',
    sort_order  INT         DEFAULT 0           COMMENT '排序',
    create_time DATETIME    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_menu_code (menu_code)
) COMMENT '系统菜单';

-- ---- 2. 菜单-权限关联表 ----
CREATE TABLE IF NOT EXISTS sys_menu_permission (
    menu_id         BIGINT NOT NULL,
    permission_id   BIGINT NOT NULL,
    PRIMARY KEY (menu_id, permission_id)
) COMMENT '菜单-权限关联';

-- ---- 3. 角色-菜单关联表 ----
CREATE TABLE IF NOT EXISTS sys_role_menu (
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, menu_id)
) COMMENT '角色-菜单关联';

-- ===== 种子数据 =====

-- ---- 菜单 ----
INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, icon, sort_order) VALUES
  -- 一级菜单（对应侧边栏）
  (1, NULL, 'chat',        '智能问答',   NULL, 1),
  (2, NULL, 'ticket',      '投诉建议',   NULL, 2),
  (3, NULL, 'admin',       '系统管理',   NULL, 3),
  -- 二级菜单（系统管理下）
  (31, 3, 'knowledge',    '知识库管理', NULL, 1),
  (32, 3, 'sensitive',    '敏感词管理', NULL, 2),
  (33, 3, 'role',         '角色管理',   NULL, 3),
  (34, 3, 'audit',        '操作审计',   NULL, 4)
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name);

-- ---- 补齐 knowledge 权限 ----
INSERT INTO sys_permission (perm_code, perm_name, resource_path, method) VALUES
  ('knowledge:list',    '知识库-列表',    '/api/v1/knowledge/documents',         'GET'),
  ('knowledge:upload',  '知识库-上传',    '/api/v1/knowledge/documents',         'POST'),
  ('knowledge:detail',  '知识库-详情',    '/api/v1/knowledge/documents/{id}',    'GET'),
  ('knowledge:update',  '知识库-编辑',    '/api/v1/knowledge/documents/{id}',    'PUT'),
  ('knowledge:status',  '知识库-状态',    '/api/v1/knowledge/documents/{id}/status', 'PATCH'),
  ('knowledge:delete',  '知识库-删除',    '/api/v1/knowledge/documents/{id}',    'DELETE'),
  ('knowledge:reindex', '知识库-重索引',  '/api/v1/knowledge/documents/{id}/reindex', 'POST'),
  ('admin:roles:view',  '角色管理-查看',  '/api/v1/admin/roles',                 'GET'),
  ('admin:roles:edit',  '角色管理-编辑',  '/api/v1/admin/roles/{id}/permissions', 'PUT'),
  ('admin:stats:view',  '管理面板-统计',  '/api/v1/admin/stats',                 'GET'),
  ('knowledge:chunks',  '知识库-分块',    '/api/v1/knowledge/documents/{id}/chunks', 'GET')
ON DUPLICATE KEY UPDATE perm_name = VALUES(perm_name);

-- ---- 菜单-权限映射 ----
-- 使用子查询动态获取 permission_id，避免硬编码
INSERT INTO sys_menu_permission (menu_id, permission_id)
SELECT m.id, p.id FROM sys_menu m, sys_permission p WHERE m.menu_code = 'chat'    AND p.perm_code IN ('chat:send','chat:list','chat:detail','chat:delete')
UNION SELECT m.id, p.id FROM sys_menu m, sys_permission p WHERE m.menu_code = 'ticket'  AND p.perm_code IN ('ticket:create','ticket:query')
UNION SELECT m.id, p.id FROM sys_menu m, sys_permission p WHERE m.menu_code = 'knowledge' AND p.perm_code LIKE 'knowledge:%'
UNION SELECT m.id, p.id FROM sys_menu m, sys_permission p WHERE m.menu_code = 'sensitive' AND p.perm_code = 'admin:sensitive-words'
UNION SELECT m.id, p.id FROM sys_menu m, sys_permission p WHERE m.menu_code = 'role'      AND p.perm_code LIKE 'admin:roles:%'
ON DUPLICATE KEY UPDATE menu_id = VALUES(menu_id);

-- ---- 角色-菜单迁移（从旧 sys_role_permission 反推） ----
-- ADMIN（role_id=1）→ 拥有所有菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu
ON DUPLICATE KEY UPDATE menu_id = VALUES(menu_id);

-- USER（role_id=3）→ 拥有 chat 和 ticket 菜单（根据旧 sys_role_permission）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 3, m.id FROM sys_menu m WHERE m.menu_code IN ('chat', 'ticket')
ON DUPLICATE KEY UPDATE menu_id = VALUES(menu_id);
