-- =====================================================================
-- 点云域：标注对象批注表扩展 3D 位置字段，用于条目级位置批注
-- 目标库: ry-vue (MySQL 8.x)
--
-- 背景:
--   3D 点云审核环节需要「条目批注」：审核员点击点云某位置，记录该位置并
--   填写批注。object_key 为 NULL 且 pos_x/pos_y/pos_z 有值时，表示条目级
--   位置批注；object_key 非 NULL 时仍为原来的实例（框级）批注。
--
-- 特性:
--   - 补列 / 补索引先查 information_schema 再执行，脚本可安全重复执行；
--   - 前置依赖：label_object_comment 已存在（label_object_group_comment_upgrade.sql）。
--
-- 执行方式:
--   mysql -h<host> -P<port> -u<user> -p <db> < label_object_comment_position_upgrade.sql
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 标注对象批注表增加 3D 位置坐标
-- ---------------------------------------------------------------------
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_object_comment' AND COLUMN_NAME = 'pos_x') = 0,
  'ALTER TABLE `label_object_comment` ADD COLUMN `pos_x` double DEFAULT NULL COMMENT ''3D 位置 X 坐标（条目位置批注使用）'' AFTER `content`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_object_comment' AND COLUMN_NAME = 'pos_y') = 0,
  'ALTER TABLE `label_object_comment` ADD COLUMN `pos_y` double DEFAULT NULL COMMENT ''3D 位置 Y 坐标（条目位置批注使用）'' AFTER `pos_x`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_object_comment' AND COLUMN_NAME = 'pos_z') = 0,
  'ALTER TABLE `label_object_comment` ADD COLUMN `pos_z` double DEFAULT NULL COMMENT ''3D 位置 Z 坐标（条目位置批注使用）'' AFTER `pos_y`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2. 补充位置索引，加速按资产查询位置批注
-- ---------------------------------------------------------------------
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_object_comment' AND INDEX_NAME = 'idx_object_comment_position') = 0,
  'ALTER TABLE `label_object_comment` ADD KEY `idx_object_comment_position` (`asset_id`, `object_key`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
