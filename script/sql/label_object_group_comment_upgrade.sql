-- =====================================================================
-- 图像域：标注对象「合组」与「框级批注」能力升级脚本
-- 目标库: ry-vue (MySQL 8.x)
--
-- 背景:
--   1) 合组：多个标注对象需要建立「同组」关系（组内成员联动高亮、
--      批量改类 / 批量删除）。原 label_annotation_object 无分组列，
--      前端只能靠 attributes 透传（无法在库内统计与校验）。
--   2) 框级批注：质检 / 审核环节需要针对「某一个框」写批注。
--      序列域已有 label_comment（帧级）与 label_annotation_error
--      （对象级），但图像域没有对应载体，标注页也无框级批注入口。
--
-- 特性:
--   - 建表使用 CREATE TABLE IF NOT EXISTS；
--   - 补列 / 补索引先查 information_schema 再执行，脚本可安全重复执行；
--   - 前置依赖：label_annotation_object 已存在（label_biz_migration.sql）。
--
-- 执行方式:
--   mysql -h<host> -P<port> -u<user> -p <db> < label_object_group_comment_upgrade.sql
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 标注对象增加分组键 group_key
--    同组对象由前端生成同一个 key；NULL 表示未分组。
-- ---------------------------------------------------------------------
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_annotation_object' AND COLUMN_NAME = 'group_key') = 0,
  'ALTER TABLE `label_annotation_object` ADD COLUMN `group_key` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT ''分组键（同组对象一致，NULL 表示未分组）'' AFTER `class_id`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_annotation_object' AND INDEX_NAME = 'idx_object_group') = 0,
  'ALTER TABLE `label_annotation_object` ADD KEY `idx_object_group` (`group_key`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2. 框级批注表（图像域对象维度）
--    与序列域 label_annotation_error 的模型保持一致：
--    以 (job_id, object_key) 定位到具体标注对象。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `label_object_comment` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '批注ID',
  `job_id` bigint(20) DEFAULT NULL COMMENT '作业ID',
  `asset_id` bigint(20) DEFAULT NULL COMMENT '资产（图片）ID',
  `object_key` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '标注对象Key',
  `node` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '产生环节（label 标注 / review 审核）',
  `content` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '批注内容',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'open' COMMENT '状态（open 待处理 / corrected 已修正）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_object_comment_job` (`job_id`),
  KEY `idx_object_comment_asset` (`asset_id`),
  KEY `idx_object_comment_object` (`object_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='图像域标注对象级批注表';

-- ---------------------------------------------------------------------
-- 3. 扩展 3D 位置坐标（点云域条目级位置批注使用）
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

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_object_comment' AND INDEX_NAME = 'idx_object_comment_position') = 0,
  'ALTER TABLE `label_object_comment` ADD KEY `idx_object_comment_position` (`asset_id`, `object_key`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
