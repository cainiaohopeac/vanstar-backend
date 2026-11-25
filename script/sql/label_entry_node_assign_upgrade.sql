-- ======================================================================
-- 标注平台「条目分配（mode=assign）补齐 label 节点记录」存量数据修复脚本
--
-- 问题背景：
--   TaskService#assignEntries 存在两条分配路径：
--     1) mode=claim（标注员「领取标注」）：updateEntryClaim 之后调用了
--        claimLabelNodeAndSyncLatest，会落库 label_entry_node(node_key='label')
--        的 claimed 记录并同步 entry.latest_node_id；
--     2) mode=assign（负责人/管理员「分配」给指定标注员）：只执行了
--        updateEntryAssign 更新 label_data_entry，漏了 label_entry_node 维护。
--
--   而任务详情页的「节点视图」列表/统计（selectEntriesByNode、
--   selectEntrySummaryByNode）与「总览」跨节点聚合（selectOverviewEntries
--   走 LEFT JOIN label_entry_node n ON n.id = e.latest_node_id）都依赖节点表：
--   缺失记录时这些条目会一直停留在「未领取（pending）」，与
--   label_data_entry.label_status='labeling' 的实际状态不一致。
--
-- 修复范围：
--   为「已分配/已领取且有标注员」但缺少 label 节点记录的条目补建
--   claimed 记录，并统一回填 entry.latest_node_id = MAX(n.id)。
--
-- 幂等性：
--   全部走 NOT EXISTS / ON DUPLICATE KEY UPDATE，可重复执行，结果一致。
-- ======================================================================


-- ######################################################################
-- 第一步：为「已分配/已领取」但缺少 label 节点记录的条目补建 claimed 记录
-- ######################################################################

INSERT INTO `label_entry_node` (
    `entry_id`, `task_id`, `node_key`, `node_type`, `status`,
    `operator_id`, `claim_time`,
    `created_by`, `updated_by`, `create_time`, `update_time`, `deleted`
)
SELECT e.`id`,
       e.`task_id`,
       'label',
       'label',
       'claimed',
       e.`assignee_id`,
       IFNULL(e.`assign_time`, NOW()),
       IFNULL(e.`created_by`, 1),
       IFNULL(e.`updated_by`, 1),
       NOW(),
       NOW(),
       0
FROM `label_data_entry` e
WHERE e.`deleted` = 0
  AND e.`task_id` IS NOT NULL
  AND e.`assignee_id` IS NOT NULL
  -- 只回填「当前确实停在标注节点」的条目：标注中 / 修订中。
  -- 已标注 / 已修订（labeled / revised）的条目当前节点应由审核节点记录决定，
  -- 强行补 label 的 claimed 记录会抢走 latest_node_id 指针，
  -- 导致总览把「已提交审核」的条目误显示为「标注节点」。
  AND e.`label_status` IN ('labeling', 'revising')
  AND NOT EXISTS (
        SELECT 1
        FROM `label_entry_node` n
        WHERE n.`entry_id` = e.`id`
          AND n.`node_key` = 'label'
          AND n.`deleted` = 0
    )
ON DUPLICATE KEY UPDATE
    `status`      = 'claimed',
    `operator_id` = VALUES(`operator_id`),
    `claim_time`  = IFNULL(VALUES(`claim_time`), `claim_time`),
    `updated_by`  = VALUES(`updated_by`),
    `update_time` = NOW();


-- ######################################################################
-- 第二步：统一回填 entry.latest_node_id = MAX(n.id)
--         （与 EntryNodeService#syncLatestNodeIds 的 MAX 语义一致）
-- ######################################################################

UPDATE `label_data_entry` e
JOIN (
    SELECT n.`entry_id`, MAX(n.`id`) AS node_id
    FROM `label_entry_node` n
    WHERE n.`deleted` = 0
    GROUP BY n.`entry_id`
) m ON m.`entry_id` = e.`id`
SET e.`latest_node_id` = m.node_id,
    e.`update_time`    = NOW()
WHERE e.`deleted` = 0
  AND (e.`latest_node_id` IS NULL OR e.`latest_node_id` <> m.node_id);
