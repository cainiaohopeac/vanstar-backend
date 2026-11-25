-- 全局属性配置改造为项目维度隔离（2026-11-19）
-- 适用：label_global_attribute（原为平台级单份配置，attr_name 单列唯一键）
-- 执行方式：bash deploy/prod/db-upgrade.sh 可复用（MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，需条件判断）
-- 注意顺序：必须先 DROP 旧唯一键再复制配置——旧键为 attr_name 单列唯一且软删行仍占键，
-- 先复制会报 Duplicate entry（187 已踩坑）。以下语句按此顺序可安全复用。

-- 1) 新增 project_id 列（NOT NULL DEFAULT 0，旧平台级行标记为 0）
ALTER TABLE label_global_attribute
    ADD COLUMN project_id bigint NOT NULL DEFAULT 0 COMMENT '所属项目ID，0=历史平台级行（已软删）' AFTER id;

-- 2) 先放开旧键（attr_name 单列唯一，软删行也占用，不先删则第 3 步必然冲突）
ALTER TABLE label_global_attribute DROP INDEX uk_global_attr_name;

-- 3) 现有平台级配置复制到每个未删除项目（项目之间互相隔离，各项目起始配置一致）
INSERT INTO label_global_attribute
    (project_id, attr_name, attr_type, attr_options, required_flag, max_length, created_by, updated_by, deleted)
SELECT p.id, g.attr_name, g.attr_type, g.attr_options, g.required_flag, g.max_length, g.created_by, g.updated_by, 0
FROM label_global_attribute g
JOIN label_project p ON p.deleted = 0
WHERE g.project_id = 0 AND g.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM label_global_attribute x
      WHERE x.project_id = p.id AND x.attr_name = g.attr_name AND x.deleted = 0
  );

-- 4) 历史平台级行软删（保留追溯，不再被查询命中）
UPDATE label_global_attribute SET deleted = 1, update_time = NOW() WHERE project_id = 0 AND deleted = 0;

-- 5) 建新唯一键 (project_id, attr_name)，upsert（ON DUPLICATE KEY UPDATE）随之按项目幂等
ALTER TABLE label_global_attribute
    ADD UNIQUE KEY uk_global_attr_name (project_id, attr_name);
