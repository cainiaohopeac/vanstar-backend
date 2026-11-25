-- ======================================================================
-- 标注平台「条目操作历史」表
--
-- 需求背景：
--   任务详情列表每个条目需要一个「条目历史」按钮，展示该条目「谁、什么时间、
--   做了什么操作」的完整流水（分配/领取标注/提交标注/领取审核/审核通过/审核驳回/
--   领取修订/提交修订/跳过/放弃/恢复/移出 等所有状态变更）。
--
-- 设计：
--   追加式（append-only）记录，只 INSERT 不 UPDATE/DELETE。
--   task_id / batch_id 在写入时从 label_data_entry 带出，避免调用方逐个反查。
--   operator_name 不落库，查询时 JOIN sys_user（nick_name 优先，回退 user_name），
--   避免用户改名后历史显示陈旧。
--
-- 幂等：CREATE TABLE IF NOT EXISTS，可重复执行。
-- 注意：本脚本需在应用启动前执行，否则历史写入会因表不存在而失败
--       （Service 层已做异常兜底，不影响主业务，但历史不会记录）。
-- ======================================================================

CREATE TABLE IF NOT EXISTS `label_entry_history` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `entry_id`    BIGINT       NOT NULL COMMENT '数据条目ID（label_data_entry.id）',
    `task_id`     BIGINT       DEFAULT NULL COMMENT '任务ID（label_task.id）',
    `batch_id`    BIGINT       DEFAULT NULL COMMENT '批次ID（label_asset_batch.id）',
    `action`      VARCHAR(64)  NOT NULL COMMENT '操作类型标识：assign/claim/submit/review_pass/...',
    `action_name` VARCHAR(64)  DEFAULT NULL COMMENT '操作中文名：分配/领取标注/提交标注/审核通过/...',
    `node_name`   VARCHAR(64)  DEFAULT NULL COMMENT '操作节点/阶段：标注/审核/任务管理员/...',
    `detail`      VARCHAR(512) DEFAULT NULL COMMENT '操作详情（备注，如分配给谁、驳回意见等）',
    `operator_id` BIGINT       DEFAULT NULL COMMENT '操作人ID',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除',
    PRIMARY KEY (`id`),
    KEY `idx_entry_time` (`entry_id`, `create_time`),
    KEY `idx_task` (`task_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '数据条目操作历史';
