-- ======================================================================
-- 项目标注文档升级脚本（项目级 PDF 参考文档，供标注页与项目详情预览）
--
-- 背景：
--   标注平台此前仅承载数据资产（图片/点云/序列）及其注释，缺少「标注文档」
--   作为标注参考的能力；标注员在标注页无法查看项目说明文档。
--   本次新增 label_project_doc 表存储项目级单 PDF 文档（项目唯一约束）：
--   · 项目详情页「标注文档」标签：项目创建者可上传/替换/删除；
--   · 标注页（image / pointCloud / sequence）通过任务 ID 反查，
--     在工具栏抽屉预览该项目文档。
--
-- 适用：已部署环境的增量升级。新环境请直接使用同步后的
--       label_biz_migration.sql 建表，无需执行本脚本。
-- 数据库：MySQL 8.x
--
-- 幂等性：加表 / 加列 / 加索引均先查 information_schema，
--         脚本可安全重复执行。
-- ======================================================================

-- ----------------------------------------------------------------------
-- 1. 标注文档表（项目维度，单文档）
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `label_project_doc` (
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