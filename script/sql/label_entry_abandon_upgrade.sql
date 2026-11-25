-- ======================================================================
-- 标注平台「条目放弃 / 恢复」增量升级脚本
--
-- 需求背景：
--   标注员可以放弃当前正在标注的条目：条目进入「已放弃(abandoned)」，
--   被放弃的条目对团队全体成员可见（可由其他成员了解进度，但不能抢占），
--   只有「放弃的那个人」可以把条目恢复（回滚到放弃前的标注状态）后继续标注。
--
-- 规则落点：
--   1) 条目标注状态新增 abandoned（字典 label_entry_status 新增一项）；
--   2) label_data_entry 记录放弃人 / 放弃时间 / 放弃前状态：
--        abandon_by          放弃人（恢复权限判定，仅该用户可恢复）
--        abandon_time        放弃时间
--        abandon_prev_status 放弃前的标注状态（恢复时回滚，缺省回退为 labeling）
--   3) 团队可见：条目列表的「我的团队」过滤对 abandoned 放行（无需按标注员归属过滤）。
--
-- 本脚本可重复执行：加列按 information_schema 判断，字典按 dict_code 判重。
-- 数据库：MySQL 8.x
-- ======================================================================


-- ######################################################################
-- 第一部分：数据条目表新增放弃相关字段
-- ######################################################################

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_data_entry' AND COLUMN_NAME = 'abandon_by') = 0,
  'ALTER TABLE `label_data_entry` ADD COLUMN `abandon_by` bigint(20) DEFAULT NULL COMMENT ''放弃人ID（已放弃条目，仅该用户可恢复）'' AFTER `review_time`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_data_entry' AND COLUMN_NAME = 'abandon_time') = 0,
  'ALTER TABLE `label_data_entry` ADD COLUMN `abandon_time` datetime DEFAULT NULL COMMENT ''放弃时间'' AFTER `abandon_by`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_data_entry' AND COLUMN_NAME = 'abandon_prev_status') = 0,
  'ALTER TABLE `label_data_entry` ADD COLUMN `abandon_prev_status` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT ''放弃前的标注状态（恢复时回滚该状态）'' AFTER `abandon_time`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 已放弃条目：单列索引（团队可见范围过滤 + 已放弃页签查询）
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_data_entry' AND INDEX_NAME = 'idx_entry_abandon') = 0,
  'ALTER TABLE `label_data_entry` ADD KEY `idx_entry_abandon` (`task_id`, `label_status`, `abandon_by`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ######################################################################
-- 第二部分：条目标注状态字典新增「已放弃」
-- ######################################################################

INSERT INTO `sys_dict_data`(dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9018, '000000', 8, '已放弃', 'abandoned', 'label_entry_status', '', 'danger', 'N', 103, 1, sysdate(), NULL, NULL, '标注员放弃当前条目，团队内可见，仅放弃人可恢复'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE dict_code = 9018);


-- ######################################################################
-- 第三部分：存量兜底（状态值、放弃字段一致性）
-- ######################################################################

-- 3.1 状态值兜底：label_status 为空的条目回到待分配
UPDATE `label_data_entry`
SET `label_status` = 'pending'
WHERE `deleted` = 0 AND (`label_status` IS NULL OR `label_status` = '');

-- 3.2 非已放弃状态的条目清理放弃字段（避免历史脏数据影响恢复权限判定）
UPDATE `label_data_entry`
SET `abandon_by` = NULL, `abandon_time` = NULL, `abandon_prev_status` = NULL
WHERE `deleted` = 0 AND `label_status` <> 'abandoned'
  AND (`abandon_by` IS NOT NULL OR `abandon_time` IS NOT NULL OR `abandon_prev_status` IS NOT NULL);

-- 3.3 已放弃但缺放弃前状态：按常见场景回退为「标注中」（恢复后可直接继续标注）
UPDATE `label_data_entry`
SET `abandon_prev_status` = 'labeling'
WHERE `deleted` = 0 AND `label_status` = 'abandoned'
  AND (`abandon_prev_status` IS NULL OR `abandon_prev_status` = '');


-- ######################################################################
-- 第四部分：执行结果自检
-- ######################################################################
SELECT 'label_data_entry.abandon_by' AS chk, COUNT(*) AS ok
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_data_entry' AND COLUMN_NAME = 'abandon_by'
UNION ALL
SELECT 'label_data_entry.abandon_time', COUNT(*)
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_data_entry' AND COLUMN_NAME = 'abandon_time'
UNION ALL
SELECT 'label_data_entry.abandon_prev_status', COUNT(*)
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_data_entry' AND COLUMN_NAME = 'abandon_prev_status'
UNION ALL
SELECT 'dict abandoned', COUNT(*) FROM sys_dict_data WHERE dict_type = 'label_entry_status' AND dict_value = 'abandoned'
UNION ALL
SELECT 'entries abandoned', COUNT(*) FROM label_data_entry WHERE deleted = 0 AND label_status = 'abandoned';
