-- ============================================================================
-- 「标签库管理」增量升级脚本
--
-- 内容：
--   1. label_library_set    —— 平台级标签库·标签集（scene 区分 2d图片/3d点云/文字）；
--   2. label_library_label  —— 标签库·标签（视觉字段与文本字段合一，scene 决定哪些生效）；
--   3. 「标签库管理」菜单（sys_menu，id=9117，标注平台目录 9090 下）。
--
-- 设计说明：
--   · 标签库是平台级共享资产，与项目完全解耦；项目侧「引入」时**复制**库中标签到
--     项目自己的标签集（label_class_set/label_class 或 label_text_class_set/label_text_class），
--     引入后项目可自由增删改，不受库后续变更影响 —— 复制语义，避免跨项目共享行
--     导致「改库影响已投产项目」的事故面。
--   · 标签字段直接对齐 label_class（视觉）与 label_text_class（文本）的既有列，
--     引入即逐列复制，不做任何转换。
--
-- 幂等性：建表 IF NOT EXISTS；菜单 INSERT 带存在性判断，可重复执行。
-- ============================================================================

-- ----------------------------------------------------------------------
-- 1. 标签库·标签集
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `label_library_set` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '标签集ID',
  `scene`       varchar(20)  NOT NULL COMMENT '适用场景：image=2d图片 / point_cloud=3d点云 / text=文字',
  `name`        varchar(100) NOT NULL COMMENT '标签集名称',
  `description` varchar(500)          DEFAULT NULL COMMENT '标签集描述',
  `sort_order`  int                   DEFAULT 0 COMMENT '排序（同场景内展示顺序）',
  `created_by`  bigint                DEFAULT NULL COMMENT '创建人',
  `updated_by`  bigint                DEFAULT NULL COMMENT '更新人',
  `create_time` datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     tinyint               NOT NULL DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_lls_scene` (`scene`, `deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '标签库·标签集（平台级，项目引入时复制）';

-- ----------------------------------------------------------------------
-- 2. 标签库·标签
--    视觉字段（tool_type/default_size_*/is_rigid/min_inside_points/keypoint_template_json）
--    与文本字段（node_type）合一存放，scene=text 的集只用 node_type，
--    image/point_cloud 的集只用视觉字段 —— 引入时按目标表逐列取用，互不干扰。
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `label_library_label` (
  `id`                     bigint       NOT NULL AUTO_INCREMENT COMMENT '标签ID',
  `set_id`                 bigint       NOT NULL COMMENT '所属标签集ID（label_library_set.id）',
  `parent_id`              bigint                DEFAULT NULL COMMENT '父标签ID（文字场景层级；NULL=顶层，引入复制时据此重建层级）',
  `label_name`             varchar(100) NOT NULL COMMENT '标签名称',
  `color`                  varchar(20)           DEFAULT '#4f8cff' COMMENT '标签颜色',
  `tool_type`              varchar(20)           DEFAULT NULL COMMENT '标注工具（bbox/polygon/brush/box3d/cuboid/polyline/keypoint/segmentation，空=不限）',
  `node_type`              varchar(20)           DEFAULT 'entity' COMMENT '文本节点类型：entity/relation/classification（scene=text 生效）',
  `default_size_l`         double                DEFAULT NULL COMMENT '3D框初始尺寸-长（米，size.x）',
  `default_size_w`         double                DEFAULT NULL COMMENT '3D框初始尺寸-宽（米，size.y）',
  `default_size_h`         double                DEFAULT NULL COMMENT '3D框初始尺寸-高（米，size.z）',
  `is_rigid`               tinyint(1)            DEFAULT 0 COMMENT '刚体（cuboid 生效）：同轨迹尺寸自动同步',
  `min_inside_points`      int                   DEFAULT NULL COMMENT '框内最少点云数（cuboid 生效）',
  `keypoint_template_json` json                  DEFAULT NULL COMMENT '关键点模板 {points:[名称],edges:[[i,j],...]}（keypoint 生效）',
  `sort_order`             int                   DEFAULT 0 COMMENT '排序',
  `status`                 varchar(20)           DEFAULT 'active' COMMENT '状态（active=启用）',
  `created_by`             bigint                DEFAULT NULL COMMENT '创建人',
  `updated_by`             bigint                DEFAULT NULL COMMENT '更新人',
  `create_time`            datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`            datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                tinyint               NOT NULL DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_lll_set` (`set_id`, `deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '标签库·标签（平台级）';

-- 已有环境补加 parent_id 列（表已存在时 CREATE TABLE IF NOT EXISTS 不会生效；
-- MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，用 information_schema 判断 + 动态 SQL 实现幂等）
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'label_library_label'
     AND COLUMN_NAME = 'parent_id') = 0,
  'ALTER TABLE `label_library_label` ADD COLUMN `parent_id` bigint DEFAULT NULL COMMENT ''父标签ID（文字场景层级；NULL=顶层，引入复制时据此重建层级）'' AFTER `set_id`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------
-- 3. 「标签库管理」菜单（标注平台目录 9090 下，侧边栏可见）
-- ----------------------------------------------------------------------
INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9117, '标签库管理', 9090, 21, 'label-library', 'label/library', '', 1, 0, 'C', '0', '0', NULL, 'tree', 103, 1, sysdate(), NULL, NULL, '平台级标签库：2d图片/3d点云/文字三页签，项目标签管理可整集引入或任选标签组成集合'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 9117);
