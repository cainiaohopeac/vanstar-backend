-- ######################################################################
-- 条目号改为「项目内唯一」：ENTRY-<项目内序号>
-- ######################################################################
-- 背景：
--   原规则为 "ENTRY-" + 全表最大 ID + 1，不同项目共用一套序号，
--   表现为项目 A 出现 ENTRY-501 时项目 B 的下一条目直接从 502 开始。
--   现改为按项目独立编号：项目 A 1..N、项目 B 也 1..N，互不影响，
--   前端展示时隐藏 ENTRY- 前缀，只显示序号。
--
-- 影响：
--   · 仅唯一索引从 (entry_no) 改为 (project_id, entry_no)；
--   · 存量数据不动（历史条目号仍是原来的全局序号，且满足新索引）；
--   · 新条目从「项目内已有最大序号 + 1」开始。
--
-- 幂等：脚本可重复执行。
-- ######################################################################

-- 1. 去掉全局唯一索引 uk_entry_no
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_data_entry' AND INDEX_NAME = 'uk_entry_no') > 0,
  'ALTER TABLE `label_data_entry` DROP INDEX `uk_entry_no`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- 2. 改成 (project_id, entry_no) 唯一：不同项目允许相同序号
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_data_entry' AND INDEX_NAME = 'uk_project_entry_no') = 0,
  'ALTER TABLE `label_data_entry` ADD UNIQUE KEY `uk_project_entry_no` (`project_id`, `entry_no`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- 3.（可选，默认不执行）若希望存量项目也从 1 重新排号，按项目内 id 顺序重写 entry_no。
--    注意：会改动历史数据的编号，且标注页 / 导出中已引用旧编号的场合会看到变化，确认后再放开执行。
--    执行前请先备份 label_data_entry。
--
-- UPDATE label_data_entry e
-- JOIN (
--   SELECT id, CONCAT('ENTRY-', ROW_NUMBER() OVER (PARTITION BY project_id ORDER BY id)) AS new_no
--   FROM label_data_entry
-- ) t ON t.id = e.id
-- SET e.entry_no = t.new_no;
