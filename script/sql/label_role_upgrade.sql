-- ======================================================================
-- 角色升级脚本（团队负责人 / 项目负责人）
--
-- 背景：平台区分两类负责人角色，role_key 与后端 LabelRoleConstants 保持一致：
--         team_leader    团队负责人：可被选为团队负责人
--         project_leader 项目负责人：可被选为项目负责人
--       新建项目时项目负责人只能从 project_leader 人员中选择；
--       新建团队时团队负责人只能从 team_leader 人员中选择。
--
-- 数据库：MySQL 8.x
-- 幂等性：角色按 role_key 判重、用户角色绑定按 (user_id, role_id) 判重，
--         脚本可安全重复执行。
-- ======================================================================

-- ----------------------------------------------------------------------
-- 1. 角色定义
-- ----------------------------------------------------------------------
INSERT INTO `sys_role` (
  `role_id`, `tenant_id`, `role_name`, `role_key`, `role_sort`, `data_scope`,
  `menu_check_strictly`, `dept_check_strictly`, `status`, `del_flag`,
  `create_dept`, `create_by`, `create_time`, `remark`
)
SELECT 110, '000000', '团队负责人', 'team_leader', 10, 1, 1, 1, '0', '0', 103, 1, NOW(), '标注平台：可被选为团队负责人'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_role` WHERE `role_key` = 'team_leader' AND `del_flag` = '0'
);

INSERT INTO `sys_role` (
  `role_id`, `tenant_id`, `role_name`, `role_key`, `role_sort`, `data_scope`,
  `menu_check_strictly`, `dept_check_strictly`, `status`, `del_flag`,
  `create_dept`, `create_by`, `create_time`, `remark`
)
SELECT 120, '000000', '项目负责人', 'project_leader', 11, 1, 1, 1, '0', '0', 103, 1, NOW(), '标注平台：可被选为项目负责人'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_role` WHERE `role_key` = 'project_leader' AND `del_flag` = '0'
);

-- ----------------------------------------------------------------------
-- 2. 为管理员（user_id = 1）绑定上述两个角色，便于初始化后即可选择负责人
--    如不需要，可直接删除本段（不影响角色定义）
-- ----------------------------------------------------------------------
INSERT INTO `sys_user_role` (`user_id`, `role_id`)
SELECT 1, r.`role_id`
FROM `sys_role` r
LEFT JOIN `sys_user_role` ur ON ur.`user_id` = 1 AND ur.`role_id` = r.`role_id`
WHERE r.`role_key` IN ('team_leader', 'project_leader')
  AND r.`del_flag` = '0'
  AND ur.`user_id` IS NULL;
