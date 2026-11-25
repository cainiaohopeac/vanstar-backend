-- ======================================================================
-- 标注平台「任务管理员」增量升级脚本
--
-- 需求背景：
--   「成员配置」中除设置普通任务成员外，还需能把「已是成员」的用户设置为「任务管理员」。
--   任务管理员与团队负责人在任务内同级：同样拥有「成员配置」权限（可继续配置成员与管理员），
--   普通任务成员与非成员不展示该入口。
--
-- 数据模型：
--   label_task_member 增加 member_role 列：
--     member —— 普通任务成员（仅可领取标注）
--     admin  —— 任务管理员（可配置任务成员，与团队负责人同级）
--
-- 兼容性说明：
--   1. 列默认值为 'member'，历史成员记录自动视作普通成员，权限不放大，无需回填数据。
--   2. 列名加 member_role 以区别于团队角色 label_team_member.team_role。
--
-- 本脚本幂等，可重复执行。数据库：MySQL 8.x
-- ======================================================================

SET @col_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'label_task_member'
    AND COLUMN_NAME = 'member_role'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `label_task_member` ADD COLUMN `member_role` varchar(20) NOT NULL DEFAULT ''member'' COMMENT ''任务内角色（member=成员，admin=管理员）'' AFTER `user_id`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
