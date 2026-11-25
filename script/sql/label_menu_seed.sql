-- =====================================================================
-- 标注平台(label) 动态菜单 sys_menu 种子脚本（供全新建库/补库时执行）
-- 对应前端: bz-frontend/src/views/label/**  RuoYi-Vue-Plus(5.6.x)
--
-- 结构说明（重要）
--   1) 本平台所有业务页面都通过登录后的 getRouters() 从 sys_menu 生成
--      动态路由（前端 constantRoutes/dynamicRoutes 不含任何 label 页面），
--      因此每个需 URL 直达的页面必须有一条 sys_menu 记录。
--   2) 全部标注页面收拢在唯一一级目录「标注平台」(9090, path=label-platform,
--      menu_type='M') 下；子菜单为 menu_type='C'、path 为目录内相对单段，
--      getRouters 由后端拼出 URL: /label-platform/<path>。
--   3) component 为前端 views 下相对路径（不含 .vue），如 path='projects'
--      对应 component='label/project/list'，命中 views/label/project/list.vue。
--   4) 主列表页 visible='0'(侧边栏显示)；标注/详情工作台 visible='1'
--      (仅注册路由、URL 直达、不占侧边栏)。
--   5) 前端页面内部跳转均使用目录前缀 URL: /label-platform/projects
--      /label-platform/tasks /label-platform/assets /label-platform/sequences
--      /label-platform/work /label-platform/reviews /label-platform/imports
--      /label-platform/exports /label-platform/dashboard
--      /label-platform/project-detail /label-platform/image-annotate
--      /label-platform/point-cloud-annotate /label-platform/sequence-annotate
--      (见 views/label 下各页 router.push 及后端 /dashboard/todos 返回值)
--   6) menu_id 采用 9090(目录)+91xx 段，避免与自带菜单(1~1xxx)冲突。
--      现网由旧版「顶级 C 平铺」升级请执行 label_menu_directory.sql（不删记录）；
--      重复执行本种子前请清理:
--      DELETE FROM sys_menu WHERE menu_id = 9090 OR menu_id BETWEEN 9100 AND 9114;
--   7) 数据权限: admin(超管)登录即见全部；其他角色需在「系统管理-角色-菜单
--      权限」勾选「标注平台」目录后(写 sys_role_menu 含目录与各子项)可见。
-- =====================================================================

-- 一级目录「标注平台」-----------------------------------------------------
INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9090, '标注平台', 0, 0, 'label-platform', NULL, '', 1, 0, 'M', '0', '0', NULL, 'platform', 103, 1, sysdate(), NULL, NULL, '标注平台一级菜单目录(收纳 91xx 标注页面)'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 9090);

-- 子菜单(侧边栏显示) -------------------------------------------------------
INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9100, '生产工作台', 9090, 1,  'dashboard',         'label/dashboard',            '', 1, 0, 'C', '0', '0', NULL, 'chart',    103, 1, sysdate(), NULL, NULL, '标注生产工作台'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 9100);
INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9101, '我的标注作业', 9090, 2, 'work',              'label/task/work',            '', 1, 0, 'C', '0', '0', NULL, 'work',     103, 1, sysdate(), NULL, NULL, '我的标注作业'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 9101);
INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9102, '项目管理',     9090, 3, 'projects',          'label/project/list',         '', 1, 0, 'C', '0', '0', NULL, 'example',  103, 1, sysdate(), NULL, NULL, '标注项目管理'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 9102);
INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9103, '任务列表',     9090, 4, 'tasks',             'label/task/list',            '', 1, 0, 'C', '0', '0', NULL, 'list',     103, 1, sysdate(), NULL, NULL, '标注任务列表'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 9103);
INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9104, '数据资产',     9090, 5, 'assets',            'label/asset/list',           '', 1, 0, 'C', '0', '0', NULL, 'database', 103, 1, sysdate(), NULL, NULL, '数据资产管理'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 9104);
INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9105, '多传感器序列', 9090, 6, 'sequences',         'label/sequence/list',        '', 1, 0, 'C', '0', '0', NULL, 'cascader', 103, 1, sysdate(), NULL, NULL, 'nuScenes 多传感器序列管理'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 9105);
INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9106, '质量审核',     9090, 7, 'reviews',           'label/review/list',          '', 1, 0, 'C', '0', '0', NULL, 'eye-open', 103, 1, sysdate(), NULL, NULL, '作业质检/验收/退回'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 9106);
INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9107, '团队协作',     9090, 8, 'teams',             'label/team/list',            '', 1, 0, 'C', '0', '0', NULL, 'peoples',  103, 1, sysdate(), NULL, NULL, '标注团队协作与工时'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 9107);
INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9108, '数据导入',     9090, 9, 'imports',           'label/import/list',          '', 1, 0, 'C', '0', '0', NULL, 'upload',   103, 1, sysdate(), NULL, NULL, '数据导入'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 9108);
INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9109, '数据导出',     9090, 10, 'exports',           'label/export/list',          '', 1, 0, 'C', '0', '0', NULL, 'download', 103, 1, sysdate(), NULL, NULL, '数据集导出'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 9109);

-- 子菜单(URL 直达、侧边栏隐藏) ----------------------------------------------
INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9110, '项目详情',     9090, 11, 'project-detail',   'label/project/detail',       '', 1, 0, 'C', '1', '0', NULL, 'example',  103, 1, sysdate(), NULL, NULL, '项目详情(隐藏)'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 9110);
INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9111, '图像标注',     9090, 12, 'image-annotate',   'label/annotate/image',       '', 1, 0, 'C', '1', '0', NULL, 'crop',     103, 1, sysdate(), NULL, NULL, '图像标注工作台(隐藏)'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 9111);
INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9112, '点云标注',     9090, 13, 'point-cloud-annotate', 'label/annotate/pointCloud', '', 1, 0, 'C', '1', '0', NULL, 'crop',     103, 1, sysdate(), NULL, NULL, '点云标注工作台(隐藏)'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 9112);
INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9113, '序列3D标注',   9090, 14, 'sequence-annotate','label/annotate/sequence',    '', 1, 0, 'C', '1', '0', NULL, 'crop',     103, 1, sysdate(), NULL, NULL, '多传感器序列3D标注(隐藏，内部渲染 label/sequence/annotate)'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 9113);
INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9114, '任务详情',     9090, 15, 'task-detail',      'label/task/detail',          '', 1, 0, 'C', '1', '0', NULL, 'list',     103, 1, sysdate(), NULL, NULL, '任务条目视图(左侧批次+右侧条目，隐藏)'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 9114);
