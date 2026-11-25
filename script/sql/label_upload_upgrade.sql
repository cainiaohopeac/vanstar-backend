-- ======================================================================
-- 批次上传数据升级脚本（项目 1—N 批次 1—N 上传数据 1—N 数据资产）
--
-- 背景：一个批次可分多次上传多个文件（zip 数据包 / 单文件），
--       每次上传对应一条「上传数据」，上传数据之间彼此独立，
--       可单独查看、单独删除，互不影响。
--
-- 适用：已部署环境的增量升级。新环境请直接使用同步后的
--       label_biz_migration.sql 建表，无需执行本脚本。
-- 数据库：MySQL 8.x
--
-- 幂等性：加表 / 加列 / 加索引均先查 information_schema，
--         历史数据回填依赖 upload_no 唯一键，脚本可安全重复执行。
-- ======================================================================

-- ----------------------------------------------------------------------
-- 1. 上传数据表（批次下的上传单元）
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `label_upload` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '上传数据ID',
  `project_id` bigint(20) NOT NULL COMMENT '项目ID',
  `batch_id` bigint(20) NOT NULL COMMENT '所属批次ID',
  `upload_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '上传编号',
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '上传文件名',
  `file_size` bigint(20) DEFAULT '0' COMMENT '上传文件大小',
  `object_name` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'MinIO 原始对象名',
  `source_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'manual' COMMENT '来源类型：zip/single/legacy',
  `asset_count` int(11) DEFAULT '0' COMMENT '入库数据条目数',
  `status` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'completed' COMMENT '处理状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_upload_no` (`upload_no`),
  KEY `idx_upload_project` (`project_id`),
  KEY `idx_upload_batch` (`batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='批次上传数据表';

-- ----------------------------------------------------------------------
-- 2. 数据资产归属上传数据
-- ----------------------------------------------------------------------
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_asset' AND COLUMN_NAME = 'upload_id') = 0,
  'ALTER TABLE `label_asset` ADD COLUMN `upload_id` bigint(20) DEFAULT NULL COMMENT ''所属上传数据ID'' AFTER `batch_id`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_asset' AND INDEX_NAME = 'idx_asset_upload') = 0,
  'ALTER TABLE `label_asset` ADD KEY `idx_asset_upload` (`upload_id`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------
-- 3. 历史数据回填：每个批次下已存在的资产归入一条「历史数据」上传记录
--    （upload_no 带唯一键，配合 INSERT IGNORE 保证重复执行不产生脏数据）
-- ----------------------------------------------------------------------
INSERT IGNORE INTO `label_upload` (
  `project_id`, `batch_id`, `upload_no`, `file_name`, `source_type`,
  `asset_count`, `status`, `remark`, `created_by`, `updated_by`
)
SELECT a.`project_id`,
       a.`batch_id`,
       CONCAT('UPLOAD-LEGACY-', a.`batch_id`),
       '历史数据',
       'legacy',
       COUNT(*),
       'completed',
       '升级脚本回填的历史上传数据',
       1, 1
FROM `label_asset` a
WHERE a.`deleted` = 0
  AND a.`upload_id` IS NULL
  AND a.`batch_id` IS NOT NULL
GROUP BY a.`project_id`, a.`batch_id`;

UPDATE `label_asset` a
    JOIN `label_upload` u
      ON u.`batch_id` = a.`batch_id` AND u.`source_type` = 'legacy' AND u.`deleted` = 0
SET a.`upload_id` = u.`id`
WHERE a.`upload_id` IS NULL
  AND a.`deleted` = 0
  AND a.`batch_id` IS NOT NULL;

-- 校正历史上传记录的数据条目数
UPDATE `label_upload` u
SET u.`asset_count` = (SELECT COUNT(*) FROM `label_asset` a
                       WHERE a.`upload_id` = u.`id` AND a.`deleted` = 0)
WHERE u.`source_type` = 'legacy' AND u.`deleted` = 0;
