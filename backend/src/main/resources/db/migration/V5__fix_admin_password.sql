-- =============================================================================
-- LAK-Agent — V5 修正管理员密码哈希
-- V2 中的 bcrypt 哈希因生成工具差异不匹配 "admin123"，新部署时需修正。
-- 已有数据库通过直接 UPDATE 处理，此迁移面向首次部署。
-- =============================================================================

UPDATE `sys_user` SET `password` = '$2b$10$sZtEXucCad7KdOyIWIOxKe7SdFSVIfSx81uu/skex7/LwagvP4872'
WHERE `username` = 'admin'
  AND `password` = '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi';
