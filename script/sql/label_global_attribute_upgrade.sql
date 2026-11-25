-- =============================================================
-- 全局属性（Global Attributes）升级脚本
-- 2026-11-17
--
-- 用途：
--   1. 全局属性配置的平台级维护（任务详情页「全局属性」tab，管理方式同标签属性）。
--   2. 图片标注页按帧给全局属性赋值，值随标注存档（label_annotation.annotation_json $.globalAttrs）。
--
-- 部署前需在目标库执行本脚本（幂等，可重复执行）。
-- =============================================================

CREATE TABLE IF NOT EXISTS `label_global_attribute` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '属性ID',
  `attr_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '属性名称',
  `attr_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'select' COMMENT '属性类型：select=下拉 / text=文本',
  `attr_options` json DEFAULT NULL COMMENT '属性选项（select 类型）',
  `required_flag` tinyint(4) DEFAULT '0' COMMENT '是否必填：1是 0否',
  `max_length` int(11) DEFAULT NULL COMMENT '文本最大字符数（仅 attr_type=text 生效，NULL 不限制）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_global_attr_name` (`attr_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='全局属性表';
