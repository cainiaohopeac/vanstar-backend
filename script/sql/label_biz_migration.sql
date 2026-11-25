-- =====================================================================
-- 标注平台业务表建表脚本(迁移自旧库 label_server)
-- 目标库: ry-vue (RuoYi-Vue-Plus 5.6.2)
-- 说明:
--   1) 表结构源于旧库 label_server 线上表(只读导出),类型/注释保持一致;
--   2) 按新平台规范补全 create_time/update_time/deleted 等通用列;
--   3) 本脚本仅建表,不含数据;如需迁移旧业务数据请单独执行数据迁移脚本。
-- 执行方式: mysql -h60.217.78.187 -P33060 -uroot -pXXXXXX ry-vue < label_biz_migration.sql
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 标注平台业务表(20 张 label_*)
-- ---------------------------------------------------------------------

CREATE TABLE `label_annotation` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '标注ID',
  `job_id` bigint(20) NOT NULL COMMENT '作业ID',
  `asset_id` bigint(20) NOT NULL COMMENT '数据ID',
  `sequence_id` bigint(20) DEFAULT NULL COMMENT '序列ID（多传感器序列标注使用）',
  `frame_id` bigint(20) DEFAULT NULL COMMENT '帧ID（多传感器序列标注使用）',
  `annotator_id` bigint(20) DEFAULT NULL COMMENT '标注员ID',
  `version` int(11) DEFAULT '1' COMMENT '版本',
  `status` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'draft' COMMENT '标注状态',
  `annotation_json` json DEFAULT NULL COMMENT '标注JSON',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='标注版本表';

CREATE TABLE `label_annotation_object` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '对象ID',
  `annotation_id` bigint(20) NOT NULL COMMENT '标注ID',
  `object_key` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '对象Key',
  `object_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '对象类型',
  `class_id` bigint(20) DEFAULT NULL COMMENT '标签ID',
  `geometry_json` json DEFAULT NULL COMMENT '几何数据',
  `attr_json` json DEFAULT NULL COMMENT '属性数据',
  `frame_id` bigint(20) DEFAULT NULL COMMENT '帧ID（多传感器序列标注使用）',
  `track_token` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '跟踪Token（跨帧实例关联）',
  `instance_id` bigint(20) DEFAULT NULL COMMENT '实例ID',
  `visibility` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '可见度',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='标注对象表';

CREATE TABLE `label_asset` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '数据ID',
  `project_id` bigint(20) NOT NULL COMMENT '项目ID',
  `batch_id` bigint(20) DEFAULT NULL COMMENT '批次ID',
  `upload_id` bigint(20) DEFAULT NULL COMMENT '所属上传数据ID',
  `media_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '媒体类型',
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '文件名',
  `file_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '文件地址',
  `thumbnail_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '缩略图地址',
  `file_size` bigint(20) DEFAULT '0' COMMENT '文件大小',
  `parse_status` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'waiting' COMMENT '解析状态',
  `status` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'available' COMMENT '业务状态',
  `meta_json` json DEFAULT NULL COMMENT '元信息',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='数据对象表';

CREATE TABLE `label_asset_batch` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '批次ID',
  `project_id` bigint(20) NOT NULL COMMENT '项目ID',
  `batch_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '批次号',
  `batch_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '批次名称',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `source_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '来源类型',
  `source_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '来源路径',
  `status` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'waiting' COMMENT '处理状态',
  `total_count` int(11) DEFAULT '0' COMMENT '总数',
  `success_count` int(11) DEFAULT '0' COMMENT '成功数',
  `failed_count` int(11) DEFAULT '0' COMMENT '失败数',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_no` (`batch_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='数据导入批次表';

CREATE TABLE `label_upload` (
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

CREATE TABLE `label_class` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '标签ID',
  `class_set_id` bigint(20) NOT NULL COMMENT '标签集ID',
  `label_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '标签名称',
  `color` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '#4f8cff' COMMENT '标签颜色',
  `tool_type` varchar(20) DEFAULT NULL COMMENT '标注工具（bbox/polygon/brush/box3d/cuboid/polyline/keypoint/segmentation，空=不限）',
  `sort_order` int(11) DEFAULT '0' COMMENT '排序',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'active' COMMENT '状态',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='标签表';

CREATE TABLE `label_class_attribute` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '属性ID',
  `class_id` bigint(20) NOT NULL COMMENT '标签ID',
  `attr_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '属性名称',
  `attr_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'select' COMMENT '属性类型',
  `attr_options` json DEFAULT NULL COMMENT '属性选项',
  `required_flag` tinyint(4) DEFAULT '0' COMMENT '是否必填',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='标签属性表';

CREATE TABLE `label_class_set` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '标签集ID',
  `project_id` bigint(20) NOT NULL COMMENT '项目ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '标签集名称',
  `version` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'v1.0' COMMENT '版本',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'active' COMMENT '状态',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='标签集表';

CREATE TABLE `label_delivery_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '交付记录ID',
  `project_id` bigint(20) NOT NULL COMMENT '项目ID',
  `export_job_id` bigint(20) DEFAULT NULL COMMENT '导出任务ID',
  `dataset_version` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '数据集版本',
  `export_format` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '导出格式',
  `asset_count` int(11) DEFAULT '0' COMMENT '条目数',
  `delivered_by` bigint(20) DEFAULT NULL COMMENT '交付人',
  `delivered_time` datetime DEFAULT NULL COMMENT '交付时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='成果交付记录表';

CREATE TABLE `label_export_job` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '导出任务ID',
  `project_id` bigint(20) NOT NULL COMMENT '项目ID',
  `export_format` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '导出格式',
  `status` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'waiting' COMMENT '状态',
  `param_json` json DEFAULT NULL COMMENT '导出参数',
  `result_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '结果地址',
  `error_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '错误信息',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='导出任务表';

CREATE TABLE `label_job` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '作业ID',
  `task_id` bigint(20) NOT NULL COMMENT '任务ID',
  `project_id` bigint(20) NOT NULL COMMENT '项目ID',
  `batch_id` bigint(20) DEFAULT NULL COMMENT '批次ID',
  `job_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '作业号',
  `data_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '数据类型',
  `assignee_id` bigint(20) DEFAULT NULL COMMENT '标注员ID',
  `reviewer_id` bigint(20) DEFAULT NULL COMMENT '审核员ID',
  `status` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'pending_assign' COMMENT '作业状态',
  `qc_status` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'waiting' COMMENT '质检状态',
  `asset_count` int(11) DEFAULT '0' COMMENT '条目数',
  `progress` int(11) DEFAULT '0' COMMENT '进度',
  `reject_count` int(11) DEFAULT '0' COMMENT '退回次数',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_no` (`job_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='标注作业表';

CREATE TABLE `label_job_asset` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `job_id` bigint(20) NOT NULL COMMENT '作业ID',
  `asset_id` bigint(20) NOT NULL COMMENT '数据ID',
  `sort_order` int(11) DEFAULT '0' COMMENT '排序',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_asset` (`job_id`,`asset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='作业数据关系表';

CREATE TABLE `label_project` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '项目ID',
  `project_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '项目编号',
  `project_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '项目名称',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '项目说明',
  `data_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '数据类型：image/point_cloud/mixed',
  `owner_id` bigint(20) DEFAULT NULL COMMENT '负责人ID',
  `team_id` bigint(20) DEFAULT NULL COMMENT '团队ID',
  `status` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'running' COMMENT '项目状态',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_code` (`project_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='标注项目表';

CREATE TABLE `label_project_member` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `project_id` bigint(20) NOT NULL COMMENT '项目ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `member_role` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '项目角色',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_user_role` (`project_id`,`user_id`,`member_role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='项目成员表';

CREATE TABLE `label_project_doc` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '标注文档ID',
  `project_id` bigint(20) NOT NULL COMMENT '项目ID（唯一：项目下最多一份文档）',
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '原始文件名',
  `object_name` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'MinIO 对象名',
  `bucket_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'MinIO bucket 名',
  `file_size` bigint(20) NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
  `content_type` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'application/pdf' COMMENT '内容类型',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '上传人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_id` (`project_id`),
  KEY `idx_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='项目标注文档表（项目维度单文档）';

CREATE TABLE `label_qc_report` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '质检报告ID',
  `project_id` bigint(20) NOT NULL COMMENT '项目ID',
  `task_id` bigint(20) DEFAULT NULL COMMENT '任务ID',
  `sample_count` int(11) DEFAULT '0' COMMENT '抽检数',
  `pass_count` int(11) DEFAULT '0' COMMENT '通过数',
  `reject_count` int(11) DEFAULT '0' COMMENT '驳回数',
  `pass_rate` decimal(5,2) DEFAULT '0.00' COMMENT '通过率',
  `problem_summary_json` json DEFAULT NULL COMMENT '问题摘要',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='质检报告表';

CREATE TABLE `label_qc_rule` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '质检规则ID',
  `project_id` bigint(20) DEFAULT NULL COMMENT '项目ID',
  `task_id` bigint(20) DEFAULT NULL COMMENT '任务ID',
  `sample_rate` decimal(5,2) DEFAULT '20.00' COMMENT '抽检比例',
  `min_sample_count` int(11) DEFAULT '1' COMMENT '最小抽检数',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'active' COMMENT '状态',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='质检规则表';

CREATE TABLE `label_review_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '审核记录ID',
  `job_id` bigint(20) NOT NULL COMMENT '作业ID',
  `asset_id` bigint(20) DEFAULT NULL COMMENT '数据ID',
  `annotation_id` bigint(20) DEFAULT NULL COMMENT '标注ID',
  `reviewer_id` bigint(20) DEFAULT NULL COMMENT '审核员ID',
  `review_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'review' COMMENT '审核类型',
  `result` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '审核结果',
  `problem_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '问题类型',
  `problem_level` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '问题等级',
  `comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '审核意见',
  `problem_json` json DEFAULT NULL COMMENT '问题定位',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='审核记录表';

CREATE TABLE `label_task` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `project_id` bigint(20) NOT NULL COMMENT '项目ID',
  `class_set_id` bigint(20) DEFAULT NULL COMMENT '标签集ID',
  `task_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务号',
  `task_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务名称',
  `data_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '数据类型',
  `creator_id` bigint(20) DEFAULT NULL COMMENT '创建人ID',
  `team_id` bigint(20) DEFAULT NULL COMMENT '团队ID',
  `status` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'pending_assign' COMMENT '任务状态',
  `job_size` int(11) DEFAULT '100' COMMENT '默认作业规模',
  `total_job_count` int(11) DEFAULT '0' COMMENT '作业数',
  `asset_count` int(11) DEFAULT '0' COMMENT '条目数',
  `completed_count` int(11) DEFAULT '0' COMMENT '完成数',
  `favorite_count` int(11) DEFAULT '0' COMMENT '收藏数',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_no` (`task_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='标注任务表';

CREATE TABLE `label_task_favorite` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `task_id` bigint(20) NOT NULL COMMENT '任务ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_user` (`task_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='任务收藏表';

CREATE TABLE `label_team` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '团队ID',
  `team_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '团队名称',
  `leader_id` bigint(20) DEFAULT NULL COMMENT '负责人ID',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'active' COMMENT '状态',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='团队表';

CREATE TABLE `label_team_member` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `team_id` bigint(20) NOT NULL COMMENT '团队ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `team_role` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '团队角色',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_team_user` (`team_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='团队成员表';

-- ---------------------------------------------------------------------
-- 2. 分片导入表(2 张)
-- ---------------------------------------------------------------------

CREATE TABLE `import_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '导入记录ID',
  `user_id` bigint(20) NOT NULL COMMENT '导入用户ID',
  `project_id` bigint(20) DEFAULT '0' COMMENT '所属项目ID',
  `batch_id` bigint(20) DEFAULT NULL COMMENT '目标数据批次ID',
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '文件名',
  `file_size` bigint(20) NOT NULL COMMENT '文件总大小（字节）',
  `object_name` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'MinIO 对象名',
  `bucket_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'MinIO 桶名',
  `upload_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'MinIO 分片上传ID',
  `chunk_size` bigint(20) NOT NULL COMMENT '分片大小（字节）',
  `total_chunks` int(11) NOT NULL COMMENT '总分片数',
  `uploaded_chunks` int(11) DEFAULT '0' COMMENT '已上传分片数',
  `uploaded_bytes` bigint(20) DEFAULT '0' COMMENT '已上传字节数',
  `status` tinyint(4) DEFAULT '0' COMMENT '状态：0待上传 1上传中 2已暂停 3已完成 4失败',
  `error_msg` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '错误信息',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `finish_time` datetime DEFAULT NULL COMMENT '完成时间',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_user_status` (`user_id`,`status`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_batch_id` (`batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='导入记录表';

CREATE TABLE `import_part` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '分片ID',
  `record_id` bigint(20) NOT NULL COMMENT '导入记录ID',
  `part_number` int(11) NOT NULL COMMENT '分片序号（从1开始）',
  `etag` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'MinIO 分片 ETag',
  `part_size` bigint(20) DEFAULT '0' COMMENT '分片大小（字节）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_record_part` (`record_id`,`part_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='导入分片表';

-- ---------------------------------------------------------------------
-- 3. 旧平台自带业务表(2 张,与 RuoYi sys_* 框架表不冲突)
-- ---------------------------------------------------------------------

CREATE TABLE `sys_notification` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '通知ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `title` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '标题',
  `content` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '内容',
  `biz_type` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '业务类型',
  `biz_id` bigint(20) DEFAULT NULL COMMENT '业务ID',
  `read_status` tinyint(4) DEFAULT '0' COMMENT '是否已读',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='通知消息表';

CREATE TABLE `sys_operation_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `user_id` bigint(20) DEFAULT NULL COMMENT '用户ID',
  `action` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '操作',
  `target_type` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '目标类型',
  `target_id` bigint(20) DEFAULT NULL COMMENT '目标ID',
  `detail_json` json DEFAULT NULL COMMENT '详情',
  `ip` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'IP',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='操作日志表';

-- ---------------------------------------------------------------------
-- 4. 项目-团队多对多关联(项目可绑定多个队伍；label_project.team_id 保留为主团队)
-- ---------------------------------------------------------------------

CREATE TABLE `label_project_team` (
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

-- 存量回填：把 label_project.team_id 迁移为多对多关联记录(幂等)
INSERT INTO `label_project_team` (`project_id`, `team_id`, `created_by`, `updated_by`)
SELECT `id`, `team_id`, `created_by`, `updated_by`
FROM `label_project`
WHERE `team_id` IS NOT NULL AND `team_id` <> ''
ON DUPLICATE KEY UPDATE `update_time` = NOW();

-- ---------------------------------------------------------------------
-- 5. 多传感器序列(nuScenes)相关表(10 张)
--    旧库 label_server 无序列能力，需按 SequenceMapper.xml 补建；
--    与 label_sequence_schema.sql 内容一致，二者执行其一即可。
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
