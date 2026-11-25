-- ======================================================================
-- 标注平台「条目跳过 / 恢复」增量升级脚本
--
-- 需求背景：
--   标注员在标注过程中遇到数据问题或过难时，可以跳过当前正在标注的条目，
--   条目进入「已跳过(skipped)」，由团队负责人/任务管理员/跳过人本人把条目
--   恢复到跳过前的标注状态，继续由原标注员完成。
--
--   与「已放弃」语义对偶：放弃只允许放弃人本人恢复，跳过允许更广的角色恢复。
--
-- 规则落点：
--   1) label_data_entry 记录跳过人 / 跳过时间 / 跳过前状态：
--        skip_by          跳过人（恢复权限判定，本人或管理员可恢复）
--        skip_time        跳过时间
--        skip_prev_status 跳过前的标注状态（恢复时回滚，缺省回退为 labeling）
--   2) 恢复权限在业务层校验：本跳过人 / 项目团队负责人 / 任务管理员。
--      skip_by 列保留索引，便于「已跳过」页签与权限过滤。
--
-- 本脚本可重复执行：加列按 information_schema 判断。
-- 数据库：MySQL 8.x
-- ======================================================================


-- ######################################################################
-- 第一部分：数据条目表新增跳过相关字段
-- ######################################################################

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_data_entry' AND COLUMN_NAME = 'skip_by') = 0,
  'ALTER TABLE `label_data_entry` ADD COLUMN `skip_by` bigint(20) DEFAULT NULL COMMENT ''跳过人ID（已跳过条目，恢复权限判定）'' AFTER `abandon_prev_status`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_data_entry' AND COLUMN_NAME = 'skip_time') = 0,
  'ALTER TABLE `label_data_entry` ADD COLUMN `skip_time` datetime DEFAULT NULL COMMENT ''跳过时间'' AFTER `skip_by`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_data_entry' AND COLUMN_NAME = 'skip_prev_status') = 0,
  'ALTER TABLE `label_data_entry` ADD COLUMN `skip_prev_status` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT ''跳过前的标注状态（恢复时回滚该状态）'' AFTER `skip_time`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 已跳过条目：单列索引（已跳过页签查询 + 恢复权限过滤）
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_data_entry' AND INDEX_NAME = 'idx_entry_skip') = 0,
  'ALTER TABLE `label_data_entry` ADD KEY `idx_entry_skip` (`task_id`, `label_status`, `skip_by`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ######################################################################
-- 第二部分：存量兜底（跳过字段一致性）
-- ######################################################################

-- 2.1 非已跳过状态的条目清理跳过字段（避免历史脏数据影响恢复权限判定）
UPDATE `label_data_entry`
SET `skip_by` = NULL, `skip_time` = NULL, `skip_prev_status` = NULL
WHERE `deleted` = 0 AND `label_status` <> 'skipped'
  AND (`skip_by` IS NOT NULL OR `skip_time` IS NOT NULL OR `skip_prev_status` IS NOT NULL);

-- 2.2 已跳过但缺跳过前状态：按常见场景回退为「标注中」（恢复后可直接继续标注）
UPDATE `label_data_entry`
SET `skip_prev_status` = 'labeling'
WHERE `deleted` = 0 AND `label_status` = 'skipped'
  AND (`skip_prev_status` IS NULL OR `skip_prev_status` = '');


-- ######################################################################
-- 第三部分：执行结果自检
-- ######################################################################
SELECT 'label_data_entry.skip_by' AS chk, COUNT(*) AS ok
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_data_entry' AND COLUMN_NAME = 'skip_by'
UNION ALL
SELECT 'label_data_entry.skip_time', COUNT(*)
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_data_entry' AND COLUMN_NAME = 'skip_time'
UNION ALL
SELECT 'label_data_entry.skip_prev_status', COUNT(*)
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_data_entry' AND COLUMN_NAME = 'skip_prev_status'
UNION ALL
SELECT 'idx_entry_skip', COUNT(*)
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_data_entry' AND INDEX_NAME = 'idx_entry_skip'
UNION ALL
SELECT 'entries skipped', COUNT(*) FROM label_data_entry WHERE deleted = 0 AND label_status = 'skipped';