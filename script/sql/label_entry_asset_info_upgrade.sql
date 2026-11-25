-- ======================================================================
-- 标注平台「label_data_entry 数据资产信息冗余列」增量升级脚本
--
-- 需求背景：
--   TaskMapper.entryColumns（任务详情-条目列表）与 ReviewMapper.reviewEntryColumns
--   （审核工作台-条目列表）原先每行执行 4/3 个 label_asset 相关子查询
--   （fileUrl / thumbnailUrl / mediaType / parseStatus），每页 20 行即 80-100 次
--   子查询，且 4 个子查询重复扫描同一批 asset 行。本脚本在 label_data_entry 上
--   增加 3 个冗余列，读取侧改为冗余列 + first_asset_id 主键 JOIN。
--
-- 冗余列语义（与原子查询完全一致，见 backfill 段）：
--   media_type     条目内未删资产媒体类型去重后超一种为 'mixed'
--                  （org.dromara.label.common.MediaType.MIXED），无资产为 NULL；
--   parse_status   条目内任一未删资产 parse_status = 'failed'
--                  （AssetParseStatus.FAILED）则 'failed'，否则 'success'
--                  （AssetParseStatus.SUCCESS）——含无资产条目（原子查询无行时
--                  SUM 为 NULL，走 ELSE 'success'，故列定义 NOT NULL DEFAULT）；
--   first_asset_id 条目内 id 最小的未删资产，列表 LEFT JOIN 取 fileUrl/thumbnailUrl。
--
-- 运行时维护入口（ProjectMapper.refreshEntryAssetInfoByUpload / ByAssetId）：
--   splitUploadEntries / attachAssetsToDefaultEntry（含 uploadAsset、批量导入）/
--   retryAsset。删除上传/批次时条目本身被软删，无需刷新。
--
-- 幂等性：DDL 按 information_schema 判断后新增；backfill 为整表重算，可重复执行。
-- =======================================================================


-- 1. 冗余列：条目聚合媒体类型（'image'/'point_cloud'/'mixed'，无资产为 NULL）
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_data_entry'
     AND COLUMN_NAME = 'media_type') = 0,
  'ALTER TABLE `label_data_entry` ADD COLUMN `media_type` VARCHAR(20) NULL DEFAULT NULL COMMENT ''条目聚合媒体类型（含 mixed；无资产为 NULL），由 refreshEntryAssetInfo* 维护''',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 冗余列：条目聚合解析状态（任一资产失败为 failed，否则 success；无资产默认 success）
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_data_entry'
     AND COLUMN_NAME = 'parse_status') = 0,
  'ALTER TABLE `label_data_entry` ADD COLUMN `parse_status` VARCHAR(20) NOT NULL DEFAULT ''success'' COMMENT ''条目聚合解析状态（failed/success），由 refreshEntryAssetInfo* 维护''',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. 冗余列：条目首个未删资产 id（列表 JOIN 取 fileUrl/thumbnailUrl）
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_data_entry'
     AND COLUMN_NAME = 'first_asset_id') = 0,
  'ALTER TABLE `label_data_entry` ADD COLUMN `first_asset_id` BIGINT NULL DEFAULT NULL COMMENT ''条目首个未删资产 id（列表 JOIN 取 fileUrl/thumbnailUrl），由 refreshEntryAssetInfo* 维护''',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4. 存量数据 backfill：整表重算（幂等）。
--    注意：不更新 update_time —— 总览/列表按 update_time 排序，backfill 刷新时间戳
--    会打乱存量展示顺序；列值重算本身与时间戳无关。
--    字面量对应常量：'mixed' → MediaType.MIXED；'failed'/'success' → AssetParseStatus。
UPDATE label_data_entry e
SET e.media_type = (
        SELECT CASE WHEN COUNT(DISTINCT a.media_type) > 1 THEN 'mixed'
                    ELSE MIN(a.media_type) END
        FROM label_asset a WHERE a.entry_id = e.id AND a.deleted = 0
    ),
    e.parse_status = (
        SELECT CASE WHEN SUM(a.parse_status = 'failed') > 0 THEN 'failed'
                    ELSE 'success' END
        FROM label_asset a WHERE a.entry_id = e.id AND a.deleted = 0
    ),
    e.first_asset_id = (
        SELECT a.id FROM label_asset a
        WHERE a.entry_id = e.id AND a.deleted = 0 ORDER BY a.id LIMIT 1
    )
WHERE e.deleted = 0;
