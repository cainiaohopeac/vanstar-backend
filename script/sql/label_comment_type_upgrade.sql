-- =====================================================================
-- 批注类型（错误内容）升级：工作流审核节点配置批注类型 + 批注表记录所选类型
-- 目标库: ry-vue (MySQL 8.x)
--
-- 背景:
--   审核节点需要「批注类型」下拉：默认漏点 / 漏标 / 不贴合 / 混点 / 类别错误，
--   项目管理员可在工作流配置里为每个审核节点增删自定义类型。
--   审核员打批注时先选类型（错误内容），再填写不超过 200 字的描述。
--
-- 特性:
--   - 补列先查 information_schema 再执行，脚本可安全重复执行；
--   - 前置依赖：label_workflow_node（label_workflow_upgrade.sql）、
--     label_object_comment（label_object_group_comment_upgrade.sql）。
--
-- 执行方式:
--   mysql -h<host> -P<port> -u<user> -p <db> < label_comment_type_upgrade.sql
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 工作流节点：审核节点可配置的批注类型（JSON 数组文本）
-- ---------------------------------------------------------------------
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_workflow_node' AND COLUMN_NAME = 'comment_types') = 0,
  'ALTER TABLE `label_workflow_node` ADD COLUMN `comment_types` json DEFAULT NULL COMMENT ''审核节点批注类型（JSON 数组，非审核节点为空）'' AFTER `remark`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2. 批注表：批注类型（错误内容），来自所属审核节点的配置
-- ---------------------------------------------------------------------
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_object_comment' AND COLUMN_NAME = 'comment_type') = 0,
  'ALTER TABLE `label_object_comment` ADD COLUMN `comment_type` varchar(64) DEFAULT NULL COMMENT ''批注类型（错误内容：漏点/漏标/不贴合/混点/类别错误等）'' AFTER `content`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
