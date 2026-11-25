-- ======================================================================
-- 标注平台「数据条目（数据集合包）」增量升级脚本
--
-- 需求背景：
--   1) 「数据条目」不是单条数据资产，而是「一个数据集合包」：
--      一次上传的数据默认整包为一个条目（不分割），上传完成后可由用户执行「分割数据」，
--      按设置把该上传的数据平均分割成多个条目。
--   2) 任务下发、领取标注、分配、跳过、领取修订、审核都以「条目」为单位流转，
--      条目下的数据资产随条目一起流转。
--
-- 层级：项目(label_project) → 批次(label_asset_batch) → 上传数据(label_upload)
--       → 数据条目(label_data_entry) → 数据资产(label_asset)
--
-- 本脚本可重复执行：
--   · 建表 / 加列 / 加索引均先查 information_schema 判断；
--   · 存量回填按 upload_id + entry_no 唯一键幂等；
--   · 状态回填为按包内数据聚合的确定性结果，重复执行结果一致。
--
-- 数据库：MySQL 8.x
-- ======================================================================


-- ######################################################################
-- 第一部分：数据条目表（label_data_entry）
-- ######################################################################

CREATE TABLE IF NOT EXISTS `label_data_entry` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '条目ID',
  `project_id` bigint(20) NOT NULL COMMENT '项目ID',
  `batch_id` bigint(20) NOT NULL COMMENT '所属批次ID',
  `upload_id` bigint(20) NOT NULL COMMENT '所属上传数据ID',
  `entry_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '条目编号（系统生成，唯一）',
  `entry_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '条目名称（默认取上传文件名，分割后追加序号）',
  `seq_no` int(11) DEFAULT '1' COMMENT '上传内序号（从1开始）',
  `asset_count` int(11) DEFAULT '0' COMMENT '条目内数据条数（实时统计）',
  `split_mode` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'whole' COMMENT '分割方式：whole不分割/count按数量/parts按份数',
  `task_id` bigint(20) DEFAULT NULL COMMENT '所属任务ID（批次下发后回填）',
  `assignee_id` bigint(20) DEFAULT NULL COMMENT '标注员ID',
  `reviewer_id` bigint(20) DEFAULT NULL COMMENT '当前审核人ID',
  `label_status` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'pending' COMMENT '条目标注状态：pending/labeling/labeled/unrevised/revising/revised/skipped/abandoned',
  `review_status` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'none' COMMENT '条目审核状态：none/reviewing/rejected/re_reviewing/re_rejected/passed',
  `review_round` int(11) DEFAULT '0' COMMENT '审核轮次（领取一次审核+1）',
  `reject_count` int(11) DEFAULT '0' COMMENT '不通过次数',
  `assign_time` datetime DEFAULT NULL COMMENT '分配时间',
  `review_time` datetime DEFAULT NULL COMMENT '最近审核时间',
  `abandon_by` bigint(20) DEFAULT NULL COMMENT '放弃人ID（已放弃条目，仅该用户可恢复）',
  `abandon_time` datetime DEFAULT NULL COMMENT '放弃时间',
  `abandon_prev_status` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '放弃前的标注状态（恢复时回滚该状态）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entry_no` (`entry_no`),
  KEY `idx_entry_project` (`project_id`),
  KEY `idx_entry_batch` (`batch_id`),
  KEY `idx_entry_upload` (`upload_id`),
  KEY `idx_entry_task` (`task_id`, `label_status`),
  KEY `idx_entry_assignee` (`task_id`, `assignee_id`),
  KEY `idx_entry_review` (`task_id`, `review_status`, `label_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='数据条目表（数据集合包）';


-- ######################################################################
-- 第二部分：数据资产归属条目
-- ######################################################################

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_asset' AND COLUMN_NAME = 'entry_id') = 0,
  'ALTER TABLE `label_asset` ADD COLUMN `entry_id` bigint(20) DEFAULT NULL COMMENT ''所属数据条目ID'' AFTER `upload_id`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_asset' AND INDEX_NAME = 'idx_asset_entry') = 0,
  'ALTER TABLE `label_asset` ADD KEY `idx_asset_entry` (`entry_id`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- 数据条目增加「上传源文件」冗余字段（拆分后每个条目可直接展示来源压缩包名）
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_data_entry' AND COLUMN_NAME = 'source_file') = 0,
  'ALTER TABLE `label_data_entry` ADD COLUMN `source_file` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT ''上传源文件（原始压缩包名，拆分条目时冗余便于直接展示）'' AFTER `entry_name`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- 审核记录补充条目维度：条目级审核记录写 entry_id，作业级记录仍保留 asset_id
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_review_record' AND COLUMN_NAME = 'entry_id') = 0,
  'ALTER TABLE `label_review_record` ADD COLUMN `entry_id` bigint(20) DEFAULT NULL COMMENT ''数据条目ID（条目级审核）'' AFTER `asset_id`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_review_record' AND INDEX_NAME = 'idx_review_record_entry') = 0,
  'ALTER TABLE `label_review_record` ADD KEY `idx_review_record_entry` (`entry_id`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ######################################################################
-- 第三部分：存量回填（每个上传数据生成一个「不分割」的整包条目）
-- ######################################################################

-- 3.1 为每个尚无条目的上传数据建立默认条目（整包 = 1 个条目）
INSERT IGNORE INTO `label_data_entry` (
  `project_id`, `batch_id`, `upload_id`, `entry_no`, `entry_name`, `seq_no`,
  `asset_count`, `split_mode`, `label_status`, `review_status`, `review_round`, `reject_count`,
  `created_by`, `updated_by`, `deleted`
)
SELECT u.`project_id`,
       u.`batch_id`,
       u.`id`,
       CONCAT('ENTRY-UPLOAD-', u.`id`),
       u.`file_name`,
       1,
       (SELECT COUNT(*) FROM `label_asset` a WHERE a.`upload_id` = u.`id` AND a.`deleted` = 0),
       'whole',
       'pending',
       'none',
       0,
       0,
       1, 1, 0
FROM `label_upload` u
WHERE u.`deleted` = 0
  AND u.`batch_id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `label_data_entry` e
                  WHERE e.`upload_id` = u.`id` AND e.`deleted` = 0);

-- 3.1.5 存量条目回填「上传源文件」（取自所属上传数据的原始文件名）
UPDATE `label_data_entry` e
JOIN `label_upload` u ON u.`id` = e.`upload_id` AND u.`deleted` = 0
SET e.`source_file` = u.`file_name`
WHERE e.`deleted` = 0 AND (e.`source_file` IS NULL OR e.`source_file` = '');

-- 3.2 未挂条目的资产归入其上传数据的条目（已分割的上传不受影响）
UPDATE `label_asset` a
JOIN `label_data_entry` e ON e.`upload_id` = a.`upload_id` AND e.`deleted` = 0
SET a.`entry_id` = e.`id`
WHERE a.`deleted` = 0
  AND a.`entry_id` IS NULL
  AND a.`upload_id` IS NOT NULL
  AND e.`seq_no` = 1;

-- 3.3 条目数据条数校正
UPDATE `label_data_entry` e
SET e.`asset_count` = (SELECT COUNT(*) FROM `label_asset` a
                       WHERE a.`entry_id` = e.`id` AND a.`deleted` = 0)
WHERE e.`deleted` = 0;

-- 3.4 条目状态回填：包内数据状态一致时沿用该状态，否则回到「待分配pending」
UPDATE `label_data_entry` e
JOIN (
  SELECT a.`entry_id`,
         COUNT(*) AS total,
         COUNT(DISTINCT a.`label_status`) AS statusKinds,
         MIN(a.`label_status`) AS statusValue,
         COUNT(DISTINCT COALESCE(a.`assignee_id`, 0)) AS assigneeKinds,
         MIN(a.`assignee_id`) AS assigneeValue,
         COUNT(DISTINCT COALESCE(a.`reviewer_id`, 0)) AS reviewerKinds,
         MIN(a.`reviewer_id`) AS reviewerValue,
         COUNT(DISTINCT COALESCE(a.`review_status`, '')) AS reviewKinds,
         MIN(a.`review_status`) AS reviewValue,
         MAX(a.`review_round`) AS reviewRoundValue,
         MAX(a.`reject_count`) AS rejectValue,
         MIN(a.`assign_time`) AS assignTimeValue,
         MAX(a.`review_time`) AS reviewTimeValue
  FROM `label_asset` a
  WHERE a.`deleted` = 0 AND a.`entry_id` IS NOT NULL
  GROUP BY a.`entry_id`
) s ON s.`entry_id` = e.`id`
SET e.`asset_count` = s.total,
    e.`label_status` = CASE WHEN s.statusKinds = 1 THEN s.statusValue ELSE 'pending' END,
    e.`assignee_id` = CASE WHEN s.assigneeKinds = 1 THEN s.assigneeValue ELSE NULL END,
    e.`reviewer_id` = CASE WHEN s.reviewerKinds = 1 THEN s.reviewerValue ELSE NULL END,
    e.`review_status` = CASE WHEN s.reviewKinds = 1 THEN s.reviewValue ELSE 'none' END,
    e.`review_round` = IFNULL(s.reviewRoundValue, 0),
    e.`reject_count` = IFNULL(s.rejectValue, 0),
    e.`assign_time` = s.assignTimeValue,
    e.`review_time` = s.reviewTimeValue
WHERE e.`deleted` = 0;

-- 3.5 条目所属任务按批次下发关系回填
UPDATE `label_data_entry` e
JOIN `label_task_batch` tb ON tb.`batch_id` = e.`batch_id` AND tb.`deleted` = 0
SET e.`task_id` = tb.`task_id`
WHERE e.`deleted` = 0 AND e.`task_id` IS NULL;

-- 3.6 存量审核记录的条目归属回填（原 asset_id 即当时的条目ID，回填为所属条目）
UPDATE `label_review_record` rr
JOIN `label_asset` a ON a.`id` = rr.`asset_id` AND a.`deleted` = 0
SET rr.`entry_id` = a.`entry_id`
WHERE rr.`entry_id` IS NULL AND a.`entry_id` IS NOT NULL;


-- ######################################################################
-- 第四部分：执行结果自检
-- ######################################################################
SELECT 'label_data_entry table' AS chk,
       COUNT(*) AS ok
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_data_entry'
UNION ALL
SELECT 'label_asset.entry_id', COUNT(*)
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_asset' AND COLUMN_NAME = 'entry_id'
UNION ALL
SELECT 'entries', COUNT(*) FROM label_data_entry WHERE deleted = 0
UNION ALL
SELECT 'assets bound', COUNT(*) FROM label_asset WHERE deleted = 0 AND entry_id IS NOT NULL
UNION ALL
SELECT 'assets unbound', COUNT(*) FROM label_asset WHERE deleted = 0 AND entry_id IS NULL;
