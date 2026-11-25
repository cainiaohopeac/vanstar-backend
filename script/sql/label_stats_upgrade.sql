-- ======================================================================
-- 标注平台「统计分析」增量升级脚本
--
-- 内容：
--   1. label_work_session —— 标注/审核有效工时采集表（前端心跳累加）；
--   2. 统计查询所需二级索引（幂等，按 information_schema 判断）；
--   3. 「统计分析」菜单（sys_menu，path=stats，component=label/stats）。
--
-- 口径说明（与 StatsMapper.xml 保持一致）：
--   · 全部产能指标以「节点完成」（label_entry_node.status = 'done'）为口径，
--     按 done_time 归属工作日；同一成员同一节点在同一日期内完成多次也算多次？
--     —— 否。uk_entry_node 保证「一条目 + 一节点」只有一行，重复返修不会重复计数，
--        工作量按「最终完成」计 1 次，避免刷量。
--   · 帧：图片/文本项目 = 资产（label_asset）粒度；序列项目 = 帧（label_frame）粒度，
--        统一用 COALESCE(annotation.frame_id, annotation.asset_id) 作为统计单元。
--   · 有标签帧 = 该统计单元下存在至少 1 个未删除标注对象；无标签帧 = 处理过但产出为空。
--   · 工时优先取 label_work_session.active_seconds；无心跳数据时回退为
--     SUM(MIN(done_time - claim_time, cap))，并在接口返回 estimated=true。
--
-- 幂等性：建表用 IF NOT EXISTS；索引与菜单均做存在性判断，可重复执行。
-- ======================================================================

-- ----------------------------------------------------------------------
-- 1. 有效工时采集表
--    前端标注工作台每 60s 上报一次心跳（页面隐藏/失焦暂停），后端累加当日秒数。
--    设计要点：
--      · 按「人 + 任务 + 节点 + 日期」唯一，避免一人多开页面重复计时；
--      · task_id 默认 0（未绑定任务的工作台入口），因 MySQL 唯一索引允许多个 NULL，
--        故用 0 兜底以保证唯一约束生效；
--      · 只存累计值，不存明细，写入为 INSERT ... ON DUPLICATE KEY UPDATE 单语句。
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `label_work_session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '成员ID（sys_user.user_id）',
  `project_id` bigint DEFAULT NULL COMMENT '项目ID',
  `task_id` bigint NOT NULL DEFAULT 0 COMMENT '任务ID（0=未绑定任务）',
  `node_key` varchar(64) NOT NULL DEFAULT 'label' COMMENT '工作流节点标识',
  `work_date` date NOT NULL COMMENT '工作日（服务器时区）',
  `active_seconds` int NOT NULL DEFAULT 0 COMMENT '当日累计有效秒数',
  `last_heartbeat_time` datetime DEFAULT NULL COMMENT '最后一次心跳时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '软删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_user_task_node_date` (`user_id`, `task_id`, `node_key`, `work_date`),
  KEY `idx_session_date_user` (`work_date`, `user_id`),
  KEY `idx_session_task` (`task_id`, `node_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标注有效工时采集（心跳累加）';

-- ----------------------------------------------------------------------
-- 2. 统计查询二级索引
-- ----------------------------------------------------------------------

-- 2.1 成员产能按「完成时间」归日：operator_id 等值 + done_time 范围
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_entry_node'
     AND INDEX_NAME = 'idx_node_operator_done') = 0,
  'ALTER TABLE `label_entry_node` ADD INDEX `idx_node_operator_done` (`operator_id`, `done_time`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2.2 节点完成量按任务 + 节点 + 完成时间过滤
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_entry_node'
     AND INDEX_NAME = 'idx_node_task_done') = 0,
  'ALTER TABLE `label_entry_node` ADD INDEX `idx_node_task_done` (`task_id`, `node_key`, `done_time`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2.3 标签/帧统计按资产反查标注（既有 idx_job_asset 以 job_id 为前缀，无法命中 asset_id）
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_annotation'
     AND INDEX_NAME = 'idx_annotation_asset') = 0,
  'ALTER TABLE `label_annotation` ADD INDEX `idx_annotation_asset` (`asset_id`, `deleted`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2.4 数据条目按任务 + 状态（统计范围收敛用）
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_data_entry'
     AND INDEX_NAME = 'idx_entry_task_status') = 0,
  'ALTER TABLE `label_data_entry` ADD INDEX `idx_entry_task_status` (`task_id`, `label_status`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------
-- 3. 「统计分析」菜单（标注平台目录 9090 下，侧边栏可见）
-- ----------------------------------------------------------------------
INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9116, '统计分析', 9090, 20, 'stats', 'label/stats', '', 1, 0, 'C', '0', '0', NULL, 'chart', 103, 1, sysdate(), NULL, NULL, '标注/审核产能、效率、质量统计分析'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 9116);
