-- =====================================================================
-- token 有效期调整为 7 天
--
-- 背景：登录 token 有效期由 sys_client 表按客户端驱动
--       （登录时 SaLoginParameter.setTimeout(client.getTimeout())，
--        见 ruoyi-admin 各 AuthStrategy），yml 的 sa-token.timeout 仅为兜底。
-- 调整前：timeout = -1（永不过期）、active_timeout = 43200（12小时无操作冻结）
-- 调整后：timeout = 604800（7 天固定有效期，7×24×3600）
--         active_timeout = -1（关闭无操作冻结，避免半天不操作即被踢下线）
--
-- 说明：
--   1. 客户端配置直查库（SysClientServiceImpl#queryByClientId），改后新登录立即生效；
--   2. 存量在线 token 仍按登录时参数（永不过期）存于 Redis，如需一并回收，
--      可删除 Redis 中 Authorization:* 相关键或等用户重新登录；
--   3. -1 为 Sa-Token「永不过期/不冻结」约定值。
-- =====================================================================

USE `ry-vue`;

UPDATE sys_client
SET timeout    = 604800,
    active_timeout = -1;
