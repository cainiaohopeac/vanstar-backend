-- ======================================================================
-- 批次下发唯一性升级脚本（同一数据批次只能下发一次任务）
--
-- 背景：任务创建时选择项目下的数据批次下发，业务规则为「一个批次仅允许被一个
--       任务下发」。此前仅靠应用层校验，本脚本在 label_task_batch 上增加
--       batch_id 唯一键，由数据库兜底，防止并发下重复下发。
--
-- 前置条件：需先执行 label_task_scope_upgrade.sql 创建 label_task_batch。
-- 数据库：MySQL 8.x
--
-- 幂等性：先清理存量重复关联，再按 information_schema 判断删旧索引 / 加唯一键，
--         脚本可安全重复执行。
-- ======================================================================

-- ----------------------------------------------------------------------
-- 1. 清理存量重复：同一批次被多个任务下发时，仅保留最早的一条关联
--    （保留最早任务，避免影响已拆分作业的任务；重复关联属历史脏数据）
-- ----------------------------------------------------------------------
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.TABLES
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_task_batch') = 0,
  'SELECT 1',
  'DELETE tb FROM `label_task_batch` tb
     JOIN `label_task_batch` keep
       ON keep.batch_id = tb.batch_id AND keep.id < tb.id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------
-- 2. 删除原 (task_id, batch_id) 联合唯一键（batch_id 唯一后其已冗余）
-- ----------------------------------------------------------------------
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_task_batch'
     AND INDEX_NAME = 'uk_task_batch') > 0,
  'ALTER TABLE `label_task_batch` DROP INDEX `uk_task_batch`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------
-- 3. 增加 batch_id 唯一键：数据库层保证批次只被下发一次
-- ----------------------------------------------------------------------
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.TABLES
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_task_batch') > 0
  AND (SELECT COUNT(*) FROM information_schema.STATISTICS
       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_task_batch'
         AND INDEX_NAME = 'uk_task_batch_unique') = 0,
  'ALTER TABLE `label_task_batch` ADD UNIQUE KEY `uk_task_batch_unique` (`batch_id`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
