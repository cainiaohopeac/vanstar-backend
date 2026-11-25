-- =====================================================================
-- 任务下发范围升级：任务-团队 多对多、任务-批次 多对多
--
-- 背景：
--   任务创建由「绑定单个团队、下发整个项目」升级为
--   「可选择项目团队池中的多支团队、下发项目下的一个或多个数据批次」。
--   label_task.team_id 保留为主团队（兼容列表/详情既有展示与查询）。
--
-- 为什么需要这个脚本：
--   仓库现有脚本没有创建 label_task_team / label_task_batch 两张关联表。
--   本脚本幂等，可重复执行。
--
-- 适用范围：MySQL（与 label_biz_migration.sql 同库结构）
-- =====================================================================

-- ---------------------------------------------------------------------
-- 第一部分：任务-团队 多对多关联表
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `label_task_team` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `task_id` bigint(20) NOT NULL COMMENT '任务ID',
  `team_id` bigint(20) NOT NULL COMMENT '团队ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_team` (`task_id`,`team_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='任务团队关联表';

-- ---------------------------------------------------------------------
-- 第二部分：任务-数据批次 多对多关联表（任务下发范围）
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `label_task_batch` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `task_id` bigint(20) NOT NULL COMMENT '任务ID',
  `batch_id` bigint(20) NOT NULL COMMENT '数据批次ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_batch` (`task_id`,`batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='任务数据批次关联表';

-- ---------------------------------------------------------------------
-- 第三部分：存量回填
-- ---------------------------------------------------------------------

-- 1) 把 label_task.team_id 主团队迁移为多团队关联记录（幂等）
INSERT INTO `label_task_team` (`task_id`, `team_id`, `created_by`, `updated_by`)
SELECT `id`, `team_id`, `created_by`, `updated_by`
FROM `label_task`
WHERE `team_id` IS NOT NULL AND `deleted` = 0
ON DUPLICATE KEY UPDATE `update_time` = NOW();

-- 2) 旧任务没有批次范围，回填所属项目最近一个数据批次（等价于升级前"下发整个项目"的实际数据来源）
INSERT INTO `label_task_batch` (`task_id`, `batch_id`, `created_by`, `updated_by`)
SELECT t.`id`, b.`id`, t.`created_by`, t.`updated_by`
FROM `label_task` t
JOIN `label_asset_batch` b
  ON b.`project_id` = t.`project_id` AND b.`deleted` = 0
WHERE t.`deleted` = 0
  AND b.`id` = (
    SELECT MAX(b2.`id`) FROM `label_asset_batch` b2
    WHERE b2.`project_id` = t.`project_id` AND b2.`deleted` = 0
  )
ON DUPLICATE KEY UPDATE `update_time` = NOW();
