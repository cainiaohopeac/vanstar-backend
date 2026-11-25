-- =====================================================================
-- 标注平台：存量库一次性增量升级脚本（自包含 / 幂等）
-- 目标库: ry-vue (MySQL 8.x)
--
-- 适用场景:
--   已部署的老环境（表结构来自旧库 label_server 快照），直接执行本脚本
--   即可把库结构补齐到 label_biz_migration.sql 的目标结构。
--
-- 为什么需要这个脚本:
--   label_biz_migration.sql 建的是"全新库"；仓库里另外两个增量脚本
--   label_sequence_schema.sql / label_batch_upgrade.sql 合起来仍有两个缺口：
--     （1）label_project_team 整张表没有任何增量脚本会创建；
--     （2）import_part / import_record / label_job_asset / label_task_favorite
--          / sys_notification / sys_operation_log 的 update_time、deleted
--          等通用列缺失。
--   本脚本 = label_sequence_schema.sql + label_batch_upgrade.sql + 上述缺口，
--   三者取并集，执行本脚本一个即可，无需再单独跑那两个。
--
-- 幂等性:
--   - 建表使用 CREATE TABLE IF NOT EXISTS；
--   - 加列 / 加索引 / 回填数据全部先查 information_schema 或使用
--     INSERT ... ON DUPLICATE KEY UPDATE，脚本可安全重复执行。
--
-- 前置条件:
--   库中已存在 label_annotation / label_annotation_object / label_asset /
--   label_asset_batch / label_project / import_record 等存量表。
--
-- 执行方式:
--   mysql -h<host> -P<port> -u<user> -p <db> < label_stock_upgrade.sql
-- =====================================================================


-- =====================================================================
-- 第一部分：多传感器序列（nuScenes）相关表（10 张）
-- =====================================================================

-- 1. 传感器
CREATE TABLE IF NOT EXISTS `label_sensor` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '传感器ID',
  `project_id` bigint(20) DEFAULT NULL COMMENT '所属项目ID（NULL 表示全局）',
  `channel` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '通道名',
  `modality` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '模态（camera/lidar/radar）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_sensor_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='传感器表';

-- 2. 传感器标定
CREATE TABLE IF NOT EXISTS `label_calibration` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '标定ID',
  `sensor_id` bigint(20) NOT NULL COMMENT '传感器ID',
  `calibration_token` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '标定Token',
  `translation` json DEFAULT NULL COMMENT '平移',
  `rotation` json DEFAULT NULL COMMENT '旋转',
  `camera_intrinsic` json DEFAULT NULL COMMENT '相机内参',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_calibration_sensor` (`sensor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='传感器标定表';

-- 3. 自车位姿
CREATE TABLE IF NOT EXISTS `label_ego_pose` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '位姿ID',
  `pose_token` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '位姿Token',
  `translation` json DEFAULT NULL COMMENT '平移',
  `rotation` json DEFAULT NULL COMMENT '旋转',
  `timestamp_us` bigint(20) DEFAULT '0' COMMENT '时间戳（微秒）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='自车位姿表';

-- 4. 序列（nuScenes 场景）
CREATE TABLE IF NOT EXISTS `label_sequence` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '序列ID',
  `project_id` bigint(20) NOT NULL COMMENT '项目ID',
  `batch_id` bigint(20) DEFAULT NULL COMMENT '所属数据批次ID',
  `sequence_token` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '序列Token',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '序列名称',
  `description` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '序列描述',
  `frame_count` int(11) DEFAULT '0' COMMENT '帧数量',
  `first_frame_id` bigint(20) DEFAULT NULL COMMENT '首帧ID',
  `last_frame_id` bigint(20) DEFAULT NULL COMMENT '末帧ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_sequence_project` (`project_id`),
  KEY `idx_sequence_batch` (`batch_id`),
  KEY `idx_sequence_token` (`project_id`,`sequence_token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='多传感器序列表';

-- 5. 序列帧
CREATE TABLE IF NOT EXISTS `label_frame` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '帧ID',
  `sequence_id` bigint(20) NOT NULL COMMENT '序列ID',
  `frame_token` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '帧Token',
  `frame_index` int(11) DEFAULT '0' COMMENT '帧序号',
  `timestamp_us` bigint(20) DEFAULT '0' COMMENT '时间戳（微秒）',
  `prev_frame_id` bigint(20) DEFAULT NULL COMMENT '上一帧ID',
  `next_frame_id` bigint(20) DEFAULT NULL COMMENT '下一帧ID',
  `is_key_frame` tinyint(4) DEFAULT '0' COMMENT '是否关键帧',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_frame_sequence` (`sequence_id`),
  KEY `idx_frame_token` (`frame_token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='序列帧表';

-- 6. 帧传感器数据
CREATE TABLE IF NOT EXISTS `label_sensor_data` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '传感器数据ID',
  `frame_id` bigint(20) NOT NULL COMMENT '帧ID',
  `sensor_id` bigint(20) DEFAULT NULL COMMENT '传感器ID',
  `calibration_id` bigint(20) DEFAULT NULL COMMENT '标定ID',
  `ego_pose_id` bigint(20) DEFAULT NULL COMMENT '位姿ID',
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '文件名',
  `file_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '文件地址',
  `file_format` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '文件格式',
  `width` int(11) DEFAULT '0' COMMENT '宽',
  `height` int(11) DEFAULT '0' COMMENT '高',
  `is_key_frame` tinyint(4) DEFAULT '0' COMMENT '是否关键帧',
  `meta_json` json DEFAULT NULL COMMENT '元信息',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_sensor_data_frame` (`frame_id`),
  KEY `idx_sensor_data_sensor` (`sensor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='帧传感器数据表';

-- 7. 目标实例（跨帧跟踪）
CREATE TABLE IF NOT EXISTS `label_instance` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '实例ID',
  `project_id` bigint(20) NOT NULL COMMENT '项目ID',
  `instance_token` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '实例Token（跟踪Token）',
  `class_id` bigint(20) DEFAULT NULL COMMENT '标签ID',
  `first_frame_id` bigint(20) DEFAULT NULL COMMENT '首次出现帧ID',
  `last_frame_id` bigint(20) DEFAULT NULL COMMENT '最后出现帧ID',
  `annotation_count` int(11) DEFAULT '0' COMMENT '标注次数',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_instance_project_token` (`project_id`,`instance_token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='目标实例表';

-- 8. 标注错误
CREATE TABLE IF NOT EXISTS `label_annotation_error` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '错误ID',
  `sequence_id` bigint(20) DEFAULT NULL COMMENT '序列ID',
  `frame_id` bigint(20) DEFAULT NULL COMMENT '帧ID',
  `object_key` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '对象Key',
  `error_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '错误类型',
  `error_level` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '错误级别',
  `status` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'open' COMMENT '处理状态',
  `comment` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '说明',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_error_sequence` (`sequence_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='标注错误表';

-- 9. 标注预检查
CREATE TABLE IF NOT EXISTS `label_precheck` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '预检查ID',
  `sequence_id` bigint(20) DEFAULT NULL COMMENT '序列ID',
  `frame_id` bigint(20) DEFAULT NULL COMMENT '帧ID',
  `object_key` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '对象Key',
  `check_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '检查类型',
  `message` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '提示信息',
  `resolved_flag` tinyint(4) DEFAULT '0' COMMENT '是否已解决',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_precheck_sequence` (`sequence_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='标注预检查表';

-- 10. 序列评论
CREATE TABLE IF NOT EXISTS `label_comment` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '评论ID',
  `sequence_id` bigint(20) DEFAULT NULL COMMENT '序列ID',
  `frame_id` bigint(20) DEFAULT NULL COMMENT '帧ID',
  `node` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '节点',
  `content` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '内容',
  `status` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'uncorrected' COMMENT '状态',
  `creator_id` bigint(20) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_comment_sequence` (`sequence_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='序列评论表';


-- =====================================================================
-- 第二部分：项目-团队多对多关联表
-- =====================================================================

CREATE TABLE IF NOT EXISTS `label_project_team` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `project_id` bigint(20) NOT NULL COMMENT '项目ID',
  `team_id` bigint(20) NOT NULL COMMENT '团队ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_team` (`project_id`,`team_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='项目团队关联表';

-- 存量回填：把 label_project.team_id 主团队迁移为多对多关联记录（幂等）
INSERT INTO `label_project_team` (`project_id`, `team_id`, `created_by`, `updated_by`)
SELECT `id`, `team_id`, `created_by`, `updated_by`
FROM `label_project`
WHERE `team_id` IS NOT NULL AND `team_id` <> ''
ON DUPLICATE KEY UPDATE `update_time` = NOW();


-- =====================================================================
-- 第三部分：存量表补列（19 列，全部带 information_schema 守卫）
-- =====================================================================

-- 3.1 数据批次表：名称与备注
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

-- 3.2 导入记录：目标批次
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'import_record' AND COLUMN_NAME = 'batch_id') = 0,
  'ALTER TABLE `import_record` ADD COLUMN `batch_id` bigint(20) DEFAULT NULL COMMENT ''目标数据批次ID'' AFTER `project_id`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3.3 导入记录：逻辑删除
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'import_record' AND COLUMN_NAME = 'deleted') = 0,
  'ALTER TABLE `import_record` ADD COLUMN `deleted` tinyint(4) DEFAULT ''0'' COMMENT ''删除标记'' AFTER `finish_time`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3.4 导入分片：更新时间 / 逻辑删除
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'import_part' AND COLUMN_NAME = 'update_time') = 0,
  'ALTER TABLE `import_part` ADD COLUMN `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'' AFTER `create_time`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'import_part' AND COLUMN_NAME = 'deleted') = 0,
  'ALTER TABLE `import_part` ADD COLUMN `deleted` tinyint(4) DEFAULT ''0'' COMMENT ''删除标记'' AFTER `update_time`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3.5 作业数据关系：更新时间 / 逻辑删除
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_job_asset' AND COLUMN_NAME = 'update_time') = 0,
  'ALTER TABLE `label_job_asset` ADD COLUMN `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'' AFTER `create_time`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_job_asset' AND COLUMN_NAME = 'deleted') = 0,
  'ALTER TABLE `label_job_asset` ADD COLUMN `deleted` tinyint(4) DEFAULT ''0'' COMMENT ''删除标记'' AFTER `update_time`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3.6 任务收藏：更新时间 / 逻辑删除
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_task_favorite' AND COLUMN_NAME = 'update_time') = 0,
  'ALTER TABLE `label_task_favorite` ADD COLUMN `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'' AFTER `create_time`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_task_favorite' AND COLUMN_NAME = 'deleted') = 0,
  'ALTER TABLE `label_task_favorite` ADD COLUMN `deleted` tinyint(4) DEFAULT ''0'' COMMENT ''删除标记'' AFTER `update_time`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3.7 通知消息：逻辑删除
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_notification' AND COLUMN_NAME = 'deleted') = 0,
  'ALTER TABLE `sys_notification` ADD COLUMN `deleted` tinyint(4) DEFAULT ''0'' COMMENT ''删除标记'' AFTER `update_time`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3.8 操作日志：更新时间 / 逻辑删除
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_operation_log' AND COLUMN_NAME = 'update_time') = 0,
  'ALTER TABLE `sys_operation_log` ADD COLUMN `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'' AFTER `create_time`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_operation_log' AND COLUMN_NAME = 'deleted') = 0,
  'ALTER TABLE `sys_operation_log` ADD COLUMN `deleted` tinyint(4) DEFAULT ''0'' COMMENT ''删除标记'' AFTER `update_time`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3.9 标注版本表：序列标注列
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_annotation' AND COLUMN_NAME = 'sequence_id') = 0,
  'ALTER TABLE `label_annotation` ADD COLUMN `sequence_id` bigint(20) DEFAULT NULL COMMENT ''序列ID（多传感器序列标注使用）'' AFTER `asset_id`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_annotation' AND COLUMN_NAME = 'frame_id') = 0,
  'ALTER TABLE `label_annotation` ADD COLUMN `frame_id` bigint(20) DEFAULT NULL COMMENT ''帧ID（多传感器序列标注使用）'' AFTER `sequence_id`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3.10 标注对象表：序列标注列
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_annotation_object' AND COLUMN_NAME = 'frame_id') = 0,
  'ALTER TABLE `label_annotation_object` ADD COLUMN `frame_id` bigint(20) DEFAULT NULL COMMENT ''帧ID（多传感器序列标注使用）'' AFTER `attr_json`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_annotation_object' AND COLUMN_NAME = 'track_token') = 0,
  'ALTER TABLE `label_annotation_object` ADD COLUMN `track_token` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT ''跟踪Token（跨帧实例关联）'' AFTER `frame_id`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_annotation_object' AND COLUMN_NAME = 'instance_id') = 0,
  'ALTER TABLE `label_annotation_object` ADD COLUMN `instance_id` bigint(20) DEFAULT NULL COMMENT ''实例ID'' AFTER `track_token`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_annotation_object' AND COLUMN_NAME = 'visibility') = 0,
  'ALTER TABLE `label_annotation_object` ADD COLUMN `visibility` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT ''可见度'' AFTER `instance_id`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- =====================================================================
-- 第四部分：补齐索引（6 个）
-- =====================================================================

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_sequence' AND INDEX_NAME = 'idx_sequence_batch') = 0,
  'ALTER TABLE `label_sequence` ADD KEY `idx_sequence_batch` (`batch_id`)',
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

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_asset' AND INDEX_NAME = 'idx_asset_batch') = 0,
  'ALTER TABLE `label_asset` ADD KEY `idx_asset_batch` (`batch_id`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_annotation' AND INDEX_NAME = 'idx_annotation_frame') = 0,
  'ALTER TABLE `label_annotation` ADD KEY `idx_annotation_frame` (`frame_id`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_annotation' AND INDEX_NAME = 'idx_annotation_sequence') = 0,
  'ALTER TABLE `label_annotation` ADD KEY `idx_annotation_sequence` (`sequence_id`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_annotation_object' AND INDEX_NAME = 'idx_object_frame') = 0,
  'ALTER TABLE `label_annotation_object` ADD KEY `idx_object_frame` (`frame_id`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- =====================================================================
-- 第五部分：历史数据回填（幂等）
-- =====================================================================

-- 5.1 批次名称用批次号兜底，保证列表可读
UPDATE `label_asset_batch`
SET `batch_name` = `batch_no`
WHERE `batch_name` IS NULL OR `batch_name` = '';

-- 5.2 历史序列归入其项目最近一个批次，避免升级后序列在批次视图里"消失"
UPDATE `label_sequence` s
    JOIN (SELECT project_id, MAX(id) AS batch_id
          FROM label_asset_batch
          WHERE deleted = 0
          GROUP BY project_id) t ON t.project_id = s.project_id
SET s.batch_id = t.batch_id
WHERE s.batch_id IS NULL;

-- =====================================================================
-- 升级完成
-- =====================================================================
