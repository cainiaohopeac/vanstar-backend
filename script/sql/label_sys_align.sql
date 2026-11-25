-- =====================================================================
-- 标注平台：label_server 结构对齐增量脚本（自包含 / 幂等 / 只增不改）
-- 目标库: label_server（MySQL 8.x）
--
-- 【生成依据】2026-09-10 对 label_server 与系统源码做的一次完整审计，方法：
--   ① 提取 ruoyi-label 模块全部 mapper(*.xml) + java 中引用的表 / 列；
--   ② 用 label_biz_migration.sql + label_stock_upgrade.sql 在临时库重建"标准结构"；
--   ③ 与 label_server 做 表 / 列 / 类型 / 索引 四级 diff。
--   审计结论：
--     * 31 张 label_* + 2 张 import_* 业务表：表名、列名、列类型、唯一键
--       与标准结构 100% 一致 —— 无缺口，本脚本刻意不动它们；
--     * 全部差异只有下面 2 处。
--
-- 【本脚本修复的差异】
--   差异 1  必须修（不修会直接抛异常 Unknown column）
--     sys_user 缺 3 列：user_id / user_name / nick_name
--     老库现有命名为 id / username / nickname，代码却按下述命名查询：
--       DashboardMapper.xml    u.user_id / u.user_name / u.nick_name / u.status
--       ProjectMapper.xml      owner.user_id / owner.user_name / owner.nick_name
--       TaskMapper.xml         u.user_id / u.user_name / u.nick_name
--       TeamMapper.xml         u.user_id / u.user_name / u.nick_name
--       ExportMapper.xml       u.user_id / u.user_name / u.nick_name
--       LabelCommonMapper.xml  user_id / user_name / nick_name
--     典型报错：Unknown column 'u.nick_name' in 'field list'
--
--   差异 2  建议修（不报错，仅影响查询性能）
--     import_record 缺索引 idx_batch_id (batch_id)
--
-- 【刻意不处理的差异】
--   sys_menu / sys_role / sys_dict_type / sys_dict_data 等 sys_* 表在本库中
--   属于老平台自定义结构，与 RuoYi-Plus 标准结构既不同名也不同型，例如：
--     * 标准 sys_menu.menu_type 为 char(1) 存 'M'/'C'/'F'，
--       而本库为 tinyint（默认 2），根本写不进 'M'/'C'；
--     * 标准 sys_menu 有 visible/query_param/is_frame/is_cache/create_dept 等列，
--       本库没有。
--   因此 label_menu_seed.sql / label_menu_directory.sql 属于 ry-vue 生态的脚本，
--   不应在 label_server 上执行（强行执行会报 Incorrect integer value）。
--   同理，框架层还依赖 sys_dept / sys_tenant / sys_post / sys_config / sys_oss /
--   sys_client / sys_logininfor / sys_notice / sys_oper_log / sys_social 等表，
--   本库整体不存在 —— 这不是"增量"能补的，系统本体应当跑在 ry-vue 上。
--
-- 【特性】
--   幂等：每条 DDL 先查 information_schema，可安全重复执行；
--   回填：新增列一律从对应旧列回填，新老两套命名数据保持一致；
--   只增不改：不删列、不改名、不改类型、不动数据语义，老平台可继续用原列。
--
-- 【执行】
--   mysql -h127.0.0.1 -P3306 -uroot -p label_server < label_sys_align.sql
-- =====================================================================


-- =====================================================================
-- 第一部分  sys_user：补齐代码使用的三列（必需）
-- =====================================================================

-- 1.1 新增 user_id 并从 id 回填
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'user_id') = 0,
  'ALTER TABLE `sys_user` ADD COLUMN `user_id` bigint(20) DEFAULT NULL COMMENT ''用户ID（与 id 同值，供新系统查询）'' AFTER `id`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `sys_user` SET `user_id` = `id` WHERE `user_id` IS NULL;

-- 1.2 user_id 唯一索引（与主键等值，一对一）
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'uk_sys_user_user_id') = 0,
  'ALTER TABLE `sys_user` ADD UNIQUE KEY `uk_sys_user_user_id` (`user_id`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 1.3 新增 user_name 并从 username 回填
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'user_name') = 0,
  'ALTER TABLE `sys_user` ADD COLUMN `user_name` varchar(50) DEFAULT NULL COMMENT ''登录账号（与 username 同值，供新系统查询）'' AFTER `username`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `sys_user` SET `user_name` = `username` WHERE `user_name` IS NULL;

-- 1.4 新增 nick_name 并从 nickname 回填
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'nick_name') = 0,
  'ALTER TABLE `sys_user` ADD COLUMN `nick_name` varchar(50) DEFAULT NULL COMMENT ''用户昵称（与 nickname 同值，供新系统查询）'' AFTER `nickname`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `sys_user` SET `nick_name` = `nickname` WHERE `nick_name` IS NULL;

-- 1.5 登录账号查询索引（代码按 user_name / nick_name 过滤与排序）
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'idx_sys_user_user_name') = 0,
  'ALTER TABLE `sys_user` ADD KEY `idx_sys_user_user_name` (`user_name`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- =====================================================================
-- 第二部分  import_record：补齐批次索引（对齐标准结构）
-- =====================================================================

-- 2.1 batch_id 索引（列表/详情按批次反查导入记录）
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'import_record' AND INDEX_NAME = 'idx_batch_id') = 0,
  'ALTER TABLE `import_record` ADD KEY `idx_batch_id` (`batch_id`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- =====================================================================
-- 第三部分  执行结果自检（每行应全部为 1）
-- =====================================================================
SELECT 'sys_user.user_id'        AS chk, COUNT(*) AS ok FROM information_schema.COLUMNS     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_user'     AND COLUMN_NAME='user_id'
UNION ALL
SELECT 'sys_user.user_name'      AS chk, COUNT(*) AS ok FROM information_schema.COLUMNS     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_user'     AND COLUMN_NAME='user_name'
UNION ALL
SELECT 'sys_user.nick_name'      AS chk, COUNT(*) AS ok FROM information_schema.COLUMNS     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_user'     AND COLUMN_NAME='nick_name'
UNION ALL
SELECT 'sys_user.uk_user_id'     AS chk, COUNT(*) AS ok FROM information_schema.STATISTICS  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_user'     AND INDEX_NAME='uk_sys_user_user_id'
UNION ALL
SELECT 'import_record.idx_batch_id' AS chk, COUNT(*) AS ok FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='import_record' AND INDEX_NAME='idx_batch_id'
UNION ALL
SELECT 'sys_user 未回填行数(应为0)' AS chk, COUNT(*) AS ok FROM `sys_user` WHERE `user_id` IS NULL OR `user_name` IS NULL OR `nick_name` IS NULL;


-- =====================================================================
-- 附录：如何让新列不再"只回填一次"（按需手工执行，本脚本不自动做）
-- =====================================================================
-- 老平台若还会新建用户，新建行不会带 user_id/user_name/nick_name。
-- 因为 MySQL 不允许触发器更新自身表、且 BEFORE INSERT 时拿不到自增 id，
-- 无法用触发器自动同步，所以需要周期性补一次（幂等，可随时重跑）：
--
--   UPDATE `sys_user` SET `user_id`   = `id`       WHERE `user_id`   IS NULL;
--   UPDATE `sys_user` SET `user_name` = `username` WHERE `user_name` IS NULL;
--   UPDATE `sys_user` SET `nick_name` = `nickname` WHERE `nick_name` IS NULL;
--
-- 若希望彻底自动化，正确做法是老平台写入时同时写新列（或把系统迁移到
-- ry-vue 上，那里 sys_user 本就是 RuoYi 标准结构，无需本脚本）。
-- =====================================================================
