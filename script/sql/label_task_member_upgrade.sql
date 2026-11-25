-- ======================================================================
-- 标注平台「任务成员配置」增量升级脚本
--
-- 需求背景：
--   任务下发到团队后，并非团队内每个人都可以领取标注。团队负责人需要先在任务详情页
--   通过「成员配置」把团队中的成员设置为「任务成员」，只有任务成员才能领取该任务的标注条目。
--
-- 数据模型：
--   label_task_member(task_id, user_id) —— 任务与「可领取成员」的多对多关联。
--   候选成员来源：任务下发团队（label_task_team → label_team_member），
--   兼容仅有 label_task.team_id 主团队的历史任务。
--
-- 兼容性说明：
--   任务尚未配置任何成员时，后端回退为「任务下发团队成员可领取」，
--   因此本脚本无需回填历史数据，不会导致存量任务无人可领取。
--
-- 本脚本幂等，可重复执行。数据库：MySQL 8.x
-- ======================================================================

CREATE TABLE IF NOT EXISTS `label_task_member` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `task_id` bigint(20) NOT NULL COMMENT '任务ID',
  `user_id` bigint(20) NOT NULL COMMENT '任务成员用户ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_user` (`task_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='任务成员表（仅任务成员可领取标注）';
