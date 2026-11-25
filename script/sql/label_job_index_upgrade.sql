-- ======================================================================
-- 标注平台「label_job 二级索引」增量升级脚本
--
-- 需求背景：
--   1. AnnotationServiceImpl#myJobs / ReviewServiceImpl#reviews 原先走
--      selectJobs(null) 全量 JOIN（task/project/user/batch 四表）加载后在
--      Java 内存过滤，300 用户并发下不可接受；已改为 SQL 下推
--      （TaskMapper#selectJobsByFilter：assignee_id / status IN 过滤）；
--   2. label_job 建表至今仅有主键 + uk_job_no，assignee_id / task_id / status
--      均无索引支撑，下推后仍需二级索引避免全表扫描。
--
-- 索引说明：
--   idx_job_assignee (assignee_id, deleted) —— 「我的作业」等值过滤；
--   idx_job_task     (task_id, deleted)     —— 任务详情/任务管理按任务查作业；
--   idx_job_status   (status, deleted)      —— 审核工作台 reviews 的 status IN 过滤。
--
-- 幂等性：索引按 information_schema 判断后新增；可重复执行。
-- =======================================================================


-- 1. 标注员维度：「我的作业」assignee_id + deleted 等值过滤
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_job' AND INDEX_NAME = 'idx_job_assignee') = 0,
  'ALTER TABLE `label_job` ADD INDEX `idx_job_assignee` (`assignee_id`, `deleted`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 任务维度：任务详情 / 任务管理按 task_id 查作业列表
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_job' AND INDEX_NAME = 'idx_job_task') = 0,
  'ALTER TABLE `label_job` ADD INDEX `idx_job_task` (`task_id`, `deleted`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. 状态维度：审核工作台按 status IN (...) 过滤（status 区分度高于 deleted，前置）
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_job' AND INDEX_NAME = 'idx_job_status') = 0,
  'ALTER TABLE `label_job` ADD INDEX `idx_job_status` (`status`, `deleted`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
