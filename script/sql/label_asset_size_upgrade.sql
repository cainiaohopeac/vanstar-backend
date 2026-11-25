-- 资产像素宽高：入库时从图片解析，供导出点位 JSON 的 size 字段使用（历史数据缺省为 NULL，导出时按需回读）
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_asset' AND COLUMN_NAME = 'width') = 0,
  'ALTER TABLE `label_asset` ADD COLUMN `width` int(11) DEFAULT NULL COMMENT ''图片像素宽（入库时解析，导出点位 JSON 用）'' AFTER `file_size`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_asset' AND COLUMN_NAME = 'height') = 0,
  'ALTER TABLE `label_asset` ADD COLUMN `height` int(11) DEFAULT NULL COMMENT ''图片像素高（入库时解析，导出点位 JSON 用）'' AFTER `width`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
