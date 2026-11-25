-- ======================================================================
-- 标注平台「数据条目级任务分配 + 任务管理/数据任务列表菜单」增量升级脚本
--
-- 需求背景：
--   1) 任务分为两个视图：
--      · 任务管理   ：项目管理员「自己创建」的任务，可查看任务详情与进度；
--      · 数据任务列表：团队负责人「接收到的」任务（下发给其负责团队），
--                      负责把任务下发的数据条目分配给标注员。
--   2) 任务创建时选择「下发团队 + 下发批次」后即下发给团队；
--      由团队负责人在任务详情页把「数据条目」按条分配
--      （领取标注 = 分配给自己；分配 = 分配给其他标注员，仅团队负责人可用）。
--      原「作业(job)」粒度过粗，升级为在 label_asset 上直接记录条目的标注归属与状态。
--
-- 本脚本为「本功能的唯一增量脚本」，集合了本次改动涉及的全部 DDL/DML，可重复执行。
-- 已有脚本（label_biz_migration.sql / label_menu_seed.sql 等）请勿再改动。
--
-- 数据库：MySQL 8.x
-- 幂等性：字段/索引按 information_schema 判断后新增，菜单/授权按 menu_id 判重，可重复执行。
-- ======================================================================


-- ######################################################################
-- 第一部分：label_asset 数据条目级分配字段
-- ######################################################################

-- ----------------------------------------------------------------------
-- 1.1 label_asset 新增字段（幂等）
-- ----------------------------------------------------------------------
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_asset' AND COLUMN_NAME = 'task_id') = 0,
  'ALTER TABLE `label_asset` ADD COLUMN `task_id` bigint(20) DEFAULT NULL COMMENT ''所属任务ID''',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_asset' AND COLUMN_NAME = 'assignee_id') = 0,
  'ALTER TABLE `label_asset` ADD COLUMN `assignee_id` bigint(20) DEFAULT NULL COMMENT ''标注员ID''',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_asset' AND COLUMN_NAME = 'reviewer_id') = 0,
  'ALTER TABLE `label_asset` ADD COLUMN `reviewer_id` bigint(20) DEFAULT NULL COMMENT ''当前审核人ID''',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_asset' AND COLUMN_NAME = 'label_status') = 0,
  'ALTER TABLE `label_asset` ADD COLUMN `label_status` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT ''pending'' COMMENT ''条目标注状态：pending/labeling/submitted/approved/rejected''',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_asset' AND COLUMN_NAME = 'reject_count') = 0,
  'ALTER TABLE `label_asset` ADD COLUMN `reject_count` int(11) DEFAULT 0 COMMENT ''不通过次数''',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_asset' AND COLUMN_NAME = 'assign_time') = 0,
  'ALTER TABLE `label_asset` ADD COLUMN `assign_time` datetime DEFAULT NULL COMMENT ''分配时间''',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------
-- 1.2 索引（幂等）
-- ----------------------------------------------------------------------
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_asset' AND INDEX_NAME = 'idx_asset_task') = 0,
  'ALTER TABLE `label_asset` ADD INDEX `idx_asset_task` (`task_id`, `label_status`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_asset' AND INDEX_NAME = 'idx_asset_assignee') = 0,
  'ALTER TABLE `label_asset` ADD INDEX `idx_asset_assignee` (`task_id`, `assignee_id`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------
-- 1.3 存量回填：按任务下发批次回填 task_id
-- ----------------------------------------------------------------------
UPDATE `label_asset` a
JOIN `label_task_batch` tb ON tb.`batch_id` = a.`batch_id` AND tb.`deleted` = 0
SET a.`task_id` = tb.`task_id`
WHERE a.`task_id` IS NULL AND a.`deleted` = 0;

-- ----------------------------------------------------------------------
-- 1.4 存量回填：按原作业(job)分配情况回填标注员/审核人/状态
-- ----------------------------------------------------------------------
UPDATE `label_asset` a
JOIN `label_job_asset` ja ON ja.`asset_id` = a.`id`
JOIN `label_job` j ON j.`id` = ja.`job_id` AND j.`deleted` = 0
SET a.`assignee_id` = COALESCE(a.`assignee_id`, j.`assignee_id`),
    a.`reviewer_id` = COALESCE(a.`reviewer_id`, j.`reviewer_id`),
    a.`reject_count` = GREATEST(COALESCE(a.`reject_count`, 0), COALESCE(j.`reject_count`, 0)),
    a.`assign_time` = COALESCE(a.`assign_time`, j.`create_time`),
    a.`label_status` = CASE
        WHEN j.`status` = 'pending_review' THEN 'submitted'
        WHEN j.`status` IN ('pending_acceptance', 'completed') THEN 'approved'
        WHEN j.`status` = 'review_rejected' THEN 'rejected'
        WHEN j.`assignee_id` IS NOT NULL THEN 'labeling'
        ELSE a.`label_status`
    END
WHERE a.`deleted` = 0
  AND a.`label_status` = 'pending'
  AND a.`task_id` IS NOT NULL;


-- ######################################################################
-- 第二部分：菜单改造（任务管理 / 数据任务列表 / 任务详情）
-- ######################################################################

-- ----------------------------------------------------------------------
-- 2.1 「任务列表(9103)」改名为「任务管理」：谁创建的谁可见
-- ----------------------------------------------------------------------
UPDATE `sys_menu` SET `menu_name` = '任务管理' WHERE `menu_id` = 9103;

-- ----------------------------------------------------------------------
-- 2.2 隐藏路由「任务详情(条目视图)」：左侧批次 + 右侧条目，供 URL 直达
--     URL: /label-platform/task-detail?id=<任务ID>[&mode=manage]
-- ----------------------------------------------------------------------
INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9114, '任务详情', 9090, 15, 'task-detail', 'label/task/detail', '', 1, 0, 'C', '1', '0', NULL, 'list', 103, 1, sysdate(), NULL, NULL, '任务详情(左侧批次+右侧条目，隐藏)'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 9114);

-- ----------------------------------------------------------------------
-- 2.3 新增侧边栏「数据任务列表(9115)」：团队负责人查看接收到的任务
--     URL: /label-platform/received-tasks
-- ----------------------------------------------------------------------
INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9115, '数据任务列表', 9090, 5, 'received-tasks', 'label/task/received', '', 1, 0, 'C', '0', '0', NULL, 'list', 103, 1, sysdate(), NULL, NULL, '团队负责人接收到的标注任务'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 9115);

-- 排序：任务管理(4) -> 数据任务列表(5) -> 数据资产(6) -> ... 依次后移
UPDATE `sys_menu` SET `order_num` = 6  WHERE `menu_id` = 9104;
UPDATE `sys_menu` SET `order_num` = 7  WHERE `menu_id` = 9105;
UPDATE `sys_menu` SET `order_num` = 8  WHERE `menu_id` = 9106;
UPDATE `sys_menu` SET `order_num` = 9  WHERE `menu_id` = 9107;
UPDATE `sys_menu` SET `order_num` = 10 WHERE `menu_id` = 9108;
UPDATE `sys_menu` SET `order_num` = 11 WHERE `menu_id` = 9109;

-- ----------------------------------------------------------------------
-- 2.4 挂到「标注平台」目录下（防御：历史记录 parent_id 异常时纠正）
-- ----------------------------------------------------------------------
UPDATE `sys_menu` SET `parent_id` = 9090 WHERE `menu_id` BETWEEN 9100 AND 9115 AND `parent_id` <> 9090;

-- ----------------------------------------------------------------------
-- 2.5 角色授权：已拥有「任务管理(9103)」的角色，自动补齐「数据任务列表(9115)」「任务详情(9114)」
-- ----------------------------------------------------------------------
INSERT INTO `sys_role_menu`(role_id, menu_id)
SELECT DISTINCT rm.`role_id`, 9115
FROM `sys_role_menu` rm
WHERE rm.`menu_id` = 9103
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id` = rm.`role_id` AND x.`menu_id` = 9115);

INSERT INTO `sys_role_menu`(role_id, menu_id)
SELECT DISTINCT rm.`role_id`, 9114
FROM `sys_role_menu` rm
WHERE rm.`menu_id` = 9103
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id` = rm.`role_id` AND x.`menu_id` = 9114);
