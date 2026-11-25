-- =====================================================================
-- 多传感器序列（nuScenes）相关表建表脚本
-- 目标库: ry-vue (MySQL 8.x)
--
-- 背景:
--   label_biz_migration.sql 迁移自旧库 label_server，旧库不具备多传感器
--   序列能力，因此下列表在仓库中一直缺失，但 SequenceMapper.xml 已完整
--   依赖它们，导致序列功能无法使用、label_batch_upgrade.sql 报"表不存在":
--     label_sensor / label_calibration / label_ego_pose
--     label_sequence / label_frame / label_sensor_data
--     label_instance / label_annotation_error / label_precheck / label_comment
--   同时 label_annotation / label_annotation_object 需要补充序列标注列。
--
-- 适用:
--   1) 新环境：直接执行 label_biz_migration.sql（已同步本脚本全部内容）；
--   2) 已部署环境：执行本脚本即可补齐缺失表与列。
--
-- 特性:
--   - 建表使用 CREATE TABLE IF NOT EXISTS；
--   - 补列 / 补索引先查 information_schema 再执行，脚本可安全重复执行；
--   - 执行前请确保已存在 label_annotation / label_annotation_object
--     （即已执行 label_biz_migration.sql）。
--
-- 执行方式:
--   mysql -h<host> -P<port> -u<user> -p <db> < label_sequence_schema.sql
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 传感器（同项目同通道复用）
-- ---------------------------------------------------------------------
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

-- ---------------------------------------------------------------------
-- 2. 传感器标定
-- ---------------------------------------------------------------------
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

-- ---------------------------------------------------------------------
-- 3. 自车位姿
-- ---------------------------------------------------------------------
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

-- ---------------------------------------------------------------------
-- 4. 序列（nuScenes 场景）
-- ---------------------------------------------------------------------
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

-- ---------------------------------------------------------------------
-- 5. 序列帧
-- ---------------------------------------------------------------------
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

-- ---------------------------------------------------------------------
-- 6. 帧传感器数据
-- ---------------------------------------------------------------------
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

-- ---------------------------------------------------------------------
-- 7. 目标实例（跨帧跟踪）
-- ---------------------------------------------------------------------
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

-- ---------------------------------------------------------------------
-- 8. 标注错误
-- ---------------------------------------------------------------------
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

-- ---------------------------------------------------------------------
-- 9. 标注预检查
-- ---------------------------------------------------------------------
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

-- ---------------------------------------------------------------------
-- 10. 序列评论
-- ---------------------------------------------------------------------
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
-- 11. 存量表补列（序列标注复用 label_annotation / label_annotation_object）
-- =====================================================================

-- 11.1 label_annotation 增加 sequence_id
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_annotation' AND COLUMN_NAME = 'sequence_id') = 0,
  'ALTER TABLE `label_annotation` ADD COLUMN `sequence_id` bigint(20) DEFAULT NULL COMMENT ''序列ID'' AFTER `asset_id`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 11.2 label_annotation 增加 frame_id
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_annotation' AND COLUMN_NAME = 'frame_id') = 0,
  'ALTER TABLE `label_annotation` ADD COLUMN `frame_id` bigint(20) DEFAULT NULL COMMENT ''帧ID'' AFTER `sequence_id`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 11.3 label_annotation_object 增加 frame_id
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_annotation_object' AND COLUMN_NAME = 'frame_id') = 0,
  'ALTER TABLE `label_annotation_object` ADD COLUMN `frame_id` bigint(20) DEFAULT NULL COMMENT ''帧ID'' AFTER `attr_json`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 11.4 label_annotation_object 增加 track_token
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_annotation_object' AND COLUMN_NAME = 'track_token') = 0,
  'ALTER TABLE `label_annotation_object` ADD COLUMN `track_token` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT ''跟踪Token'' AFTER `frame_id`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 11.5 label_annotation_object 增加 instance_id
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_annotation_object' AND COLUMN_NAME = 'instance_id') = 0,
  'ALTER TABLE `label_annotation_object` ADD COLUMN `instance_id` bigint(20) DEFAULT NULL COMMENT ''实例ID'' AFTER `track_token`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 11.6 label_annotation_object 增加 visibility
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_annotation_object' AND COLUMN_NAME = 'visibility') = 0,
  'ALTER TABLE `label_annotation_object` ADD COLUMN `visibility` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT ''可见度'' AFTER `instance_id`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 11.7 序列标注按帧 / 序列查询索引
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
