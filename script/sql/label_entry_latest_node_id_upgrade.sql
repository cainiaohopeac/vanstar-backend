-- ======================================================================
-- 标注平台「条目冗余最新节点指针」增量升级脚本
--
-- 需求背景：
--   任务详情「总览」SELECT / 计数 / 分布统计三个查询都用 LEFT JOIN label_entry_node n
--   ON n.id = (SELECT MAX(n2.id) FROM label_entry_node n2 WHERE n2.entry_id = e.id
--   AND n2.task_id = #{taskId} AND n2.deleted = 0)，每行 e 触发一次相关子查询，
--   数据量大时性能差（行数 × 3 SQL × 子查询 IO）。
--
-- 改造方案：
--   在 label_data_entry 上冗余 latest_node_id 字段（指向该 entry 在其当前 task
--   下最近一次流转到的 label_entry_node.id），所有「总览」查询改为：
--       LEFT JOIN label_entry_node n ON n.id = e.latest_node_id
--   一次主键 JOIN，无相关子查询，性能提升一个数量级。
--
-- 维护规则：
--   INSERT label_entry_node（单条 / 批量 INSERT IGNORE / INSERT ON DUPLICATE KEY UPDATE）
--   三个入口在 service 层 INSERT 完成后立刻 UPDATE entry.latest_node_id = MAX(n.id)
--   WHERE n.entry_id = e.id AND n.task_id = e.task_id AND n.deleted = 0；
--   UPDATE label_entry_node 类（claim/skip/abandon/restore/...）只改状态不改 id，
--   latest_node_id 不需要动（id 没变）。
--
-- 幂等性：列 / 索引按 information_schema 判断后新增；可重复执行；存量 backfill
-- 用 MAX(n.id) GROUP BY entry_id 一次性回填，重复执行结果一致。
-- ======================================================================


-- ######################################################################
-- 第一部分：label_data_entry 新增 latest_node_id 字段
-- ######################################################################

-- 1.1 最新流转到的 entry_node.id（冗余字段，避免总览相关子查询）
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_data_entry' AND COLUMN_NAME = 'latest_node_id') = 0,
  'ALTER TABLE `label_data_entry` ADD COLUMN `latest_node_id` bigint(20) DEFAULT NULL COMMENT ''最近一次流转到的 entry_node.id（冗余字段，避免总览相关子查询）'' AFTER `task_id`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 1.2 索引：便于反查 entry_node 主键（LEFT JOIN n ON n.id = e.latest_node_id 用主键）
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_data_entry' AND INDEX_NAME = 'idx_entry_latest_node') = 0,
  'ALTER TABLE `label_data_entry` ADD KEY `idx_entry_latest_node` (`latest_node_id`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ######################################################################
-- 第二部分：存量数据 backfill
-- ######################################################################

-- 2.1 为已有 entry_node 记录的条目回填 latest_node_id = MAX(n.id) GROUP BY entry_id
--     没节点记录的 entry 留 NULL（总览 LEFT JOIN 时 n 为 NULL → COALESCE 兜底为 pending）
UPDATE `label_data_entry` e
JOIN (
    SELECT n.`entry_id`, MAX(n.`id`) AS node_id
    FROM `label_entry_node` n
    WHERE n.`deleted` = 0
    GROUP BY n.`entry_id`
) m ON m.entry_id = e.id
SET e.`latest_node_id` = m.node_id,
    e.`updated_by` = 1,
    e.`update_time` = NOW()
WHERE e.`deleted` = 0
  AND (e.`latest_node_id` IS NULL OR e.`latest_node_id` <> m.node_id);