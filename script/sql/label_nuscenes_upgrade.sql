-- ----------------------------------------------------------------------
-- nuScenes 数据集导入 / 预标注引入 / 原格式导出 支撑表
--
-- 业务背景：
-- 1. 上传 nuScenes 压缩包时，v1.0-*/ 目录下的元数据 JSON（sample_data /
--    sample_annotation / ego_pose / calibrated_sensor 等）原样归档到 MinIO，
--    支撑「bin 资产 <-> 相机参考图」关联、自动预标注与原格式导出还原。
-- 2. 任务可开启「引入预标注」：标注工作台把预标注框注入画布，
--    标注员可删改，首次保存即转正为正式标注。
-- ----------------------------------------------------------------------

-- 1. 任务级预标注引入开关（默认关闭，存量任务行为不变）
ALTER TABLE `label_task`
  ADD COLUMN `pre_annotation_enabled` tinyint(1) DEFAULT '0' COMMENT '是否引入预标注（0 关闭 / 1 开启）';

-- 2. nuScenes 元数据归档（一条上传数据一份，重复上传覆盖）
CREATE TABLE IF NOT EXISTS `label_nuscenes_meta` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '元数据ID',
  `project_id` bigint(20) NOT NULL COMMENT '项目ID',
  `batch_id` bigint(20) DEFAULT NULL COMMENT '数据批次ID',
  `upload_id` bigint(20) NOT NULL COMMENT '上传数据ID',
  `version_dir` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'nuScenes 版本目录名（v1.0-mini / v1.0-trainval 等）',
  `meta_object_key` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '元数据压缩包的 MinIO 对象Key（v1.0-* 下全部 JSON 原样打包）',
  `status` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'parsed' COMMENT '状态：parsed 已解析 / failed 解析失败',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '解析结果说明（失败原因 / 预标注统计）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_nusc_meta_upload` (`upload_id`),
  KEY `idx_nusc_meta_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='nuScenes元数据归档表';

-- 3. 点云资产 <-> nuScenes 相机图关联（一个点云样本对应多相机通道，一通道一行）
CREATE TABLE IF NOT EXISTS `label_nuscenes_asset_map` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '关联ID',
  `project_id` bigint(20) NOT NULL COMMENT '项目ID',
  `upload_id` bigint(20) DEFAULT NULL COMMENT '上传数据ID',
  `asset_id` bigint(20) NOT NULL COMMENT '点云数据资产ID（bin/pcd）',
  `sample_token` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'nuScenes 样本Token',
  `sample_data_token` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '点云 sample_data Token（导出坐标逆变换用）',
  `camera_file_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '同帧相机图片的访问路径（file_url）',
  `camera_asset_id` bigint(20) DEFAULT NULL COMMENT '同帧相机图片资产ID',
  `camera_channel` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '相机通道（CAM_FRONT 等）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_nusc_map_asset` (`asset_id`),
  KEY `idx_nusc_map_upload` (`upload_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='nuScenes点云资产相机图关联表';

-- 4. 每帧自车位姿（ego pose）：
-- 逐帧点云原本各自在自己的雷达/自车坐标系里，自车行驶会让静态背景在切帧时整体漂移。
-- 补上每帧 ego_pose 后，标注工作台可把各帧点云统一到同一全局坐标系，背景不再抖动。
ALTER TABLE `label_nuscenes_asset_map`
  ADD COLUMN `ego_rotation` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '该帧自车位姿四元数 [w,x,y,z]（JSON）',
  ADD COLUMN `ego_translation` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '该帧自车位姿平移 [x,y,z]（JSON）';
