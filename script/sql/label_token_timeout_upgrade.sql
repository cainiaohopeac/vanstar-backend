-- ======================================================================
-- Token 有效期升级脚本（无操作 12 小时过期 + 操作自动续期）
--
-- 背景：登录 token 的「活跃超时」与「固定超时」由 sys_client 表按客户端维护，
--       登录时（PasswordAuthStrategy / SmsAuthStrategy / SocialAuthStrategy /
--       XcxAuthStrategy）读取 client.getActiveTimeout() / client.getTimeout()
--       写入 SaLoginParameter：
--         model.setTimeout(client.getTimeout());
--         model.setActiveTimeout(client.getActiveTimeout());
--       框架默认 active_timeout = 1800（30 分钟无操作即失效）、
--       timeout = 604800（7 天固定过期），对标注作业场景偏短。
--
-- 目标：token 过期时间 = 「无操作 12 小时」，期间任意一次请求都会刷新活跃超时
--       （Sa-Token 的动态活跃超时已在 ruoyi-common-satoken/src/main/resources/
--        common-satoken.yml 中开启：dynamic-active-timeout: true），
--       因此只要持续操作就不会掉线。
--
--   active_timeout = 43200 → 12 小时：最后一次操作后静默 12 小时即失效，操作自动续期
--   timeout        = -1    → 不设固定过期上限，完全由活跃超时决定
--                            （如希望保留一个固定上限，可改为 2592000 = 30 天，
--                             即持续操作最长 30 天后强制重新登录）
--
-- 数据库：MySQL 8.x
-- 幂等性：先查 information_schema 判断表是否存在，再动态执行，可安全重复执行。
-- 生效范围：全部启用中的客户端（pc / app）；如需按端区分，请自行补充 device_type 条件。
-- 缓存说明：客户端信息有 SYS_CLIENT 缓存，直接改库后若未生效，可在
--           「系统管理 - 客户端管理」中对相应客户端点击一次修改保存以清除缓存，
--           或重启应用。
-- ======================================================================

-- ----------------------------------------------------------------------
-- 1. 更新全部启用中客户端的 token 有效期
-- ----------------------------------------------------------------------
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.TABLES
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_client') = 0,
  'SELECT 1',
  'UPDATE `sys_client`
      SET `active_timeout` = 43200,
          `timeout` = -1
    WHERE `del_flag` = ''0''');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------
-- 2. 结果校验：应输出 active_timeout = 43200、timeout = -1
-- ----------------------------------------------------------------------
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.TABLES
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_client') = 0,
  'SELECT ''sys_client 表不存在，请先执行系统初始化脚本 ry_vue_5.X.sql'' AS notice',
  'SELECT `client_id`, `client_key`, `device_type`, `active_timeout`, `timeout`, `status`
     FROM `sys_client` WHERE `del_flag` = ''0''');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
