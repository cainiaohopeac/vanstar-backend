-- =====================================================================
-- 单帧无效功能升级脚本
-- 多帧条目（图像域 label_asset）中，个别帧可能损坏/无关，需支持单帧标记无效：
--   frame_flag      帧有效性标记（normal 正常 / invalid 无效）
--   invalid_reason  无效原因
--   invalid_by      操作人
--   invalid_time    操作时间
-- 标记无效后，该帧不参与导出、质检、审核统计口径，前端只读禁止新增标注。
-- =====================================================================

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_asset' AND COLUMN_NAME = 'frame_flag') = 0,
  'ALTER TABLE `label_asset` ADD COLUMN `frame_flag` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT ''normal'' COMMENT ''帧有效性标记：normal 正常 / invalid 无效''',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_asset' AND COLUMN_NAME = 'invalid_reason') = 0,
  'ALTER TABLE `label_asset` ADD COLUMN `invalid_reason` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT ''无效原因''',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_asset' AND COLUMN_NAME = 'invalid_by') = 0,
  'ALTER TABLE `label_asset` ADD COLUMN `invalid_by` bigint(20) DEFAULT NULL COMMENT ''标记无效操作人ID''',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_asset' AND COLUMN_NAME = 'invalid_time') = 0,
  'ALTER TABLE `label_asset` ADD COLUMN `invalid_time` datetime DEFAULT NULL COMMENT ''标记无效时间''',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 执行结果自检
-- =====================================================================
SELECT 'label_asset.frame_flag'     AS chk, COUNT(*) AS ok FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_asset' AND COLUMN_NAME = 'frame_flag'
UNION ALL
SELECT 'label_asset.invalid_reason', COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_asset' AND COLUMN_NAME = 'invalid_reason'
UNION ALL
SELECT 'label_asset.invalid_by',     COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_asset' AND COLUMN_NAME = 'invalid_by'
UNION ALL
SELECT 'label_asset.invalid_time',   COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_asset' AND COLUMN_NAME = 'invalid_time';
