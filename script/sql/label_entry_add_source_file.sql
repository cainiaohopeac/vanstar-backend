-- ======================================================================
-- 标注平台 增量升级脚本：数据条目增加「上传源文件」字段
--
-- 适用场景：库已存在（拥有 label_data_entry / label_upload 等表），
--           仅需在现有表上新增冗余字段并回填。
--
-- 可重复执行：加列前先查 information_schema 判断是否已存在。
-- 数据库：MySQL 8.x
-- ======================================================================


-- 1) 数据条目表增加 source_file 列（原始压缩包名，拆分条目时冗余便于直接展示）
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME   = 'label_data_entry'
     AND COLUMN_NAME  = 'source_file') = 0,
  'ALTER TABLE `label_data_entry`
     ADD COLUMN `source_file` varchar(255)
     CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci
     DEFAULT NULL
     COMMENT ''上传源文件（原始压缩包名，拆分条目时冗余便于直接展示）''
     AFTER `entry_name`',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- 2) 存量条目回填 source_file（取自所属上传数据的原始文件名）
UPDATE `label_data_entry` e
JOIN `label_upload` u ON u.`id` = e.`upload_id` AND u.`deleted` = 0
SET e.`source_file` = u.`file_name`
WHERE e.`deleted` = 0
  AND (e.`source_file` IS NULL OR e.`source_file` = '');


-- 3) 执行结果自检
SELECT
  CASE
    WHEN COUNT(*) > 0 THEN 'source_file column OK'
    ELSE 'source_file column MISSING'
  END AS chk
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME  = 'label_data_entry'
  AND COLUMN_NAME = 'source_file';

SELECT
  COUNT(*) AS entries_with_source_file
FROM `label_data_entry`
WHERE `deleted` = 0 AND `source_file` IS NOT NULL AND `source_file` <> '';
