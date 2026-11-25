-- ======================================================================
-- 数据批次升级脚本（项目 1—N 数据批次）
--
-- 背景：项目数据分多次导入，每次导入对应一个独立批次，
--       每个批次拥有自己的数据资产列表与多传感器序列列表。
--
-- 适用：已部署环境的增量升级。新环境请直接使用同步后的
--       label_biz_migration.sql 建表，无需执行本脚本。
-- 数据库：MySQL 8.x
--
-- 前置条件：
--   本脚本会修改 label_sequence；若该表尚未创建，请先执行
--   label_sequence_schema.sql 补齐序列相关表，再执行本脚本。
--
-- 幂等性：所有加列 / 加索引均先查 information_schema，
--         脚本可安全重复执行。
-- ======================================================================

-- ----------------------------------------------------------------------
-- 1. 批次表增加名称与备注（名称支持重命名）
-- ----------------------------------------------------------------------
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_asset_batch' AND COLUMN_NAME = 'batch_name') = 0,
  'ALTER TABLE `label_asset_batch` ADD COLUMN `batch_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT ''批次名称'' AFTER `batch_no`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_asset_batch' AND COLUMN_NAME = 'remark') = 0,
  'ALTER TABLE `label_asset_batch` ADD COLUMN `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT ''备注'' AFTER `batch_name`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------
-- 2. 多传感器序列归属批次
-- ----------------------------------------------------------------------
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_sequence' AND COLUMN_NAME = 'batch_id') = 0,
  'ALTER TABLE `label_sequence` ADD COLUMN `batch_id` bigint(20) DEFAULT NULL COMMENT ''所属数据批次ID'' AFTER `project_id`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_sequence' AND INDEX_NAME = 'idx_sequence_batch') = 0,
  'ALTER TABLE `label_sequence` ADD KEY `idx_sequence_batch` (`batch_id`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------
-- 3. 导入记录记录目标批次
-- ----------------------------------------------------------------------
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'import_record' AND COLUMN_NAME = 'batch_id') = 0,
  'ALTER TABLE `import_record` ADD COLUMN `batch_id` bigint(20) DEFAULT NULL COMMENT ''目标数据批次ID'' AFTER `project_id`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'import_record' AND INDEX_NAME = 'idx_import_record_batch') = 0,
  'ALTER TABLE `import_record` ADD KEY `idx_import_record_batch` (`batch_id`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------
-- 4. 资产按批次查询索引
-- ----------------------------------------------------------------------
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_asset' AND INDEX_NAME = 'idx_asset_batch') = 0,
  'ALTER TABLE `label_asset` ADD KEY `idx_asset_batch` (`batch_id`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------
-- 5. 历史批次回填名称（用批次号兜底，保证列表可读）
-- ----------------------------------------------------------------------
UPDATE `label_asset_batch`
SET `batch_name` = `batch_no`
WHERE `batch_name` IS NULL OR `batch_name` = '';

-- ----------------------------------------------------------------------
-- 6. 历史序列归入其项目最近一个批次，避免升级后序列在批次视图里"消失"
-- ----------------------------------------------------------------------
UPDATE `label_sequence` s
    JOIN (SELECT project_id, MAX(id) AS batch_id
          FROM label_asset_batch
          WHERE deleted = 0
          GROUP BY project_id) t ON t.project_id = s.project_id
SET s.batch_id = t.batch_id
WHERE s.batch_id IS NULL;
