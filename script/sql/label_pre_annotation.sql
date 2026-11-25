-- ----------------------------------------------------------------------
-- 预标注（Pre-Annotation）
--
-- 业务背景：nuScenes 数据集自带官方标注（v1.0-trainval/sample_annotation.json +
-- category.json + instance.json）。导入数据时这些标注被忽略，本功能在
-- 「数据资产」页的数据条目列表 / 项目->批次->上传数据列表 上提供「预标注」入口，
-- 解析上传的 nuScenes 压缩包，把官方 3D 框转成平台预标注结果，
-- 按文件名关联到对应的数据资产（label_asset）。
--
-- 坐标系：nuScenes 标注为 global 系，落库前转换为雷达传感器系
-- （global -> ego -> sensor），与标注工作台 cuboid 几何一致。
-- ----------------------------------------------------------------------

-- 1. 预标注（一个数据资产一份，重复预标注覆盖旧结果）
CREATE TABLE IF NOT EXISTS `label_pre_annotation` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '预标注ID',
  `project_id` bigint(20) NOT NULL COMMENT '项目ID',
  `batch_id` bigint(20) DEFAULT NULL COMMENT '数据批次ID',
  `upload_id` bigint(20) DEFAULT NULL COMMENT '上传数据ID',
  `entry_id` bigint(20) DEFAULT NULL COMMENT '数据条目ID',
  `asset_id` bigint(20) NOT NULL COMMENT '数据资产ID',
  `source` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'nuscenes' COMMENT '预标注来源',
  `source_file` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '解析所用压缩包名',
  `sample_token` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'nuScenes 样本Token',
  `sensor_channel` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '坐标系所属传感器通道',
  `box_count` int(11) DEFAULT '0' COMMENT '预标注框数量',
  `status` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'ready' COMMENT '状态：ready 已生成/adopted 已采纳/discarded 已丢弃',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_pre_asset` (`asset_id`),
  KEY `idx_pre_upload` (`upload_id`),
  KEY `idx_pre_batch` (`batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='预标注表';

-- 2. 预标注对象（3D 框，几何格式与标注工作台 cuboid 一致）
CREATE TABLE IF NOT EXISTS `label_pre_annotation_object` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '预标注对象ID',
  `pre_annotation_id` bigint(20) NOT NULL COMMENT '预标注ID',
  `asset_id` bigint(20) DEFAULT NULL COMMENT '数据资产ID（冗余，便于按资产清理）',
  `object_key` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '对象Key',
  `object_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'cuboid' COMMENT '对象类型',
  `class_id` bigint(20) DEFAULT NULL COMMENT '标签ID（映射到的项目类别，未匹配为空）',
  `class_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '源类别名（nuScenes category.name）',
  `geometry_json` json DEFAULT NULL COMMENT '几何数据（center/size/rotationZ）',
  `attr_json` json DEFAULT NULL COMMENT '属性数据（实例Token/可见度/点数等）',
  `track_token` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '实例Token（跨帧关联）',
  `visibility` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '可见度',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_pre_object` (`pre_annotation_id`),
  KEY `idx_pre_object_asset` (`asset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='预标注对象表';
