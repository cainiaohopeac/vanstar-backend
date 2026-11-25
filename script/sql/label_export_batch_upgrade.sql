-- ======================================================================
-- 导出批次粒度升级
-- 导出范围由「项目全部合格数据」细化为「指定批次（已完成）的合格数据」
-- 执行方式：在业务库手动执行本脚本后重启后端
-- ======================================================================

-- 1. 导出任务表增加批次ID列（存量任务 batch_id 为 NULL，保持项目全量口径，兼容旧任务下载）
ALTER TABLE `label_export_job`
    ADD COLUMN `batch_id` bigint(20) DEFAULT NULL COMMENT '导出批次ID（批次粒度导出）' AFTER `project_id`;

-- 2. 项目+批次查询索引
ALTER TABLE `label_export_job`
    ADD INDEX `idx_export_project_batch` (`project_id`, `batch_id`);
