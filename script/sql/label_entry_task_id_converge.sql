-- ======================================================================
-- 标注平台「条目任务归属收敛」数据校正脚本
--
-- 背景：
--   条目（label_data_entry）归属任务原有两份来源：
--     ① label_data_entry.task_id —— 建条目时按批次下发状态写入，批次下发时回填；
--     ② label_task_batch        —— 任务下发批次关联表。
--   查询侧用 (e.task_id = ? OR e.batch_id IN 本任务下发批次) 取并集兜底，由此产生两类问题：
--     · 批次被移出任务后条目 task_id 未清理，条目仍出现在原任务的总览 / 节点视图；
--     · 存量数据 task_id 为空时只能靠批次分支命中，索引无法收敛为单列范围扫描。
--   本次收敛：task_id 作为条目归属任务的唯一依据。
--
-- 执行顺序（重要）：
--   先执行本脚本校正存量 → 再发布收口后的后端代码。
--   顺序颠倒会出现「代码只看 task_id、存量 task_id 仍为空」的漏数据窗口。
--
-- 建议：执行前备份 label_data_entry（结构 + 数据）。
--       两条 UPDATE 走全表关联，数据量大时建议低峰期执行。
--
-- 可重复执行：两步 UPDATE 均为幂等收敛，重复执行结果一致。
-- 前置：已执行 label_entry_package_upgrade.sql、label_task_scope_upgrade.sql。
-- 数据库：MySQL 8.x
-- ======================================================================


-- ######################################################################
-- 第 0 步：执行前体检（只读，确认为预期数量后再继续）
-- ######################################################################

-- 0.1 待回填：批次已下发给某个任务，但条目 task_id 为空或指向别的任务
SELECT COUNT(*) AS pending_backfill
FROM label_data_entry e
JOIN label_task_batch tb ON tb.batch_id = e.batch_id AND tb.deleted = 0
JOIN label_task t ON t.id = tb.task_id AND t.deleted = 0
WHERE e.deleted = 0
  AND (e.task_id IS NULL OR e.task_id <> tb.task_id);

-- 0.2 待清空：条目 task_id 有值，但该批次已不再下发给这个任务（含批次已无任何归属）
SELECT COUNT(*) AS pending_unbind
FROM label_data_entry e
WHERE e.deleted = 0
  AND e.task_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM label_task_batch tb
                  WHERE tb.deleted = 0
                    AND tb.batch_id = e.batch_id
                    AND tb.task_id = e.task_id);

-- 0.3 脏数据：同一批次被下发给多个任务（收敛前需人工决策，本脚本不猜归属）
--     label_task_batch 的唯一键是 uk_task_batch(task_id, batch_id)，约束不到「一个批次只属于一个任务」，
--     该不变量目前只由业务层 selectOccupiedBatches 校验保证。
SELECT tb.batch_id,
       GROUP_CONCAT(DISTINCT tb.task_id ORDER BY tb.task_id) AS task_ids,
       COUNT(DISTINCT tb.task_id) AS task_count
FROM label_task_batch tb
JOIN label_task t ON t.id = tb.task_id AND t.deleted = 0
WHERE tb.deleted = 0
GROUP BY tb.batch_id
HAVING task_count > 1;


-- ######################################################################
-- 第 1 步：回填归属（以批次当前下发任务为准）
--   只处理「批次明确归属唯一任务」的情形；0.3 列出的多任务批次保持原样，避免猜错归属。
-- ######################################################################

UPDATE label_data_entry e
JOIN (
    SELECT tb.batch_id, MIN(tb.task_id) AS task_id
    FROM label_task_batch tb
    JOIN label_task t ON t.id = tb.task_id AND t.deleted = 0
    WHERE tb.deleted = 0
    GROUP BY tb.batch_id
    HAVING COUNT(DISTINCT tb.task_id) = 1
) owner ON owner.batch_id = e.batch_id
SET e.task_id = owner.task_id,
    e.update_time = NOW()
WHERE e.deleted = 0
  AND (e.task_id IS NULL OR e.task_id <> owner.task_id);


-- ######################################################################
-- 第 2 步：清空失效归属（批次未下发给该条目所挂任务）
--   覆盖「批次被移出任务」产生的历史脏数据；已被其它任务接手的批次在第 1 步已改写为正确任务，
--   此处按 tb.task_id = e.task_id 判定，不会误清。
-- ######################################################################

UPDATE label_data_entry e
SET e.task_id = NULL,
    e.update_time = NOW()
WHERE e.deleted = 0
  AND e.task_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM label_task_batch tb
                  WHERE tb.deleted = 0
                    AND tb.batch_id = e.batch_id
                    AND tb.task_id = e.task_id);


-- ######################################################################
-- 第 3 步：校验（除 0.3 列出的多任务批次外，期望两个计数均为 0）
-- ######################################################################

-- 3.1 批次已下发，但条目归属不一致
SELECT COUNT(*) AS remain_mismatch
FROM label_data_entry e
JOIN label_task_batch tb ON tb.batch_id = e.batch_id AND tb.deleted = 0
JOIN label_task t ON t.id = tb.task_id AND t.deleted = 0
WHERE e.deleted = 0
  AND (e.task_id IS NULL OR e.task_id <> tb.task_id);

-- 3.2 条目 task_id 有值，但批次已不再下发给该任务
SELECT COUNT(*) AS remain_stale
FROM label_data_entry e
WHERE e.deleted = 0
  AND e.task_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM label_task_batch tb
                  WHERE tb.deleted = 0
                    AND tb.batch_id = e.batch_id
                    AND tb.task_id = e.task_id);


-- ######################################################################
-- 附：可选收紧（默认不执行）
--   收敛后「一个批次最多下发给一个任务」成为硬不变量，可在确认 0.3 无结果后加唯一键，
--   让并发重复下发由数据库兜底（目前只靠业务层校验 + DuplicateKeyException 兜底，
--   而 uk_task_batch(task_id, batch_id) 拦不住跨任务重复）。
--   注意：若历史数据存在同批次多任务，加键会失败，须先清理。
-- ######################################################################

-- ALTER TABLE `label_task_batch` ADD UNIQUE KEY `uk_task_batch_id` (`batch_id`);
