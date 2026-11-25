-- =====================================================================
-- 标注平台(label) 菜单目录化迁移脚本
-- 目标: 把原平铺在顶层的 91xx 标注页面收拢到一级目录「标注平台」(menu_id=9090)
-- 适用: 已执行过 label_menu_seed.sql(旧版顶级 C 结构) 的现网库原地升级
-- 特性: 幂等可重跑; 不删除任何菜单记录(保留 sys_role_menu 关联)
--
-- 目录化后 URL 变化(全部加 /label-platform 前缀, 子 path 去父段/绝对段):
--   顶层 M: path=label-platform  -> Layout 路由 /label-platform
--   子项 C: path 为目录内唯一相对段, URL = /label-platform/<path>
--   9101: label/work            -> work             (/label-platform/work)
--   9110: projects/detail       -> project-detail   (/label-platform/project-detail)
--   9111: annotate/image        -> image-annotate   (/label-platform/image-annotate)
--   9112: annotate/point-cloud  -> point-cloud-annotate
--   9113: annotate/sequence     -> sequence-annotate
--   其余 path 已是单段, 保持不变 (projects/tasks/assets/... )
--   前端页面内部跳转与后端 /dashboard/todos 返回的跳转路径需同步加该前缀
-- =====================================================================

-- 1) 新增一级目录「标注平台」(幂等)
INSERT INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9090, '标注平台', 0, 0, 'label-platform', NULL, '', 1, 0, 'M', '0', '0', NULL, 'platform', 103, 1, sysdate(), NULL, NULL, '标注平台一级菜单目录(收纳 91xx 标注页面)'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 9090);

-- 2) 91xx 页面全部挂到目录下(幂等)
UPDATE sys_menu SET parent_id = 9090
WHERE menu_id BETWEEN 9100 AND 9114;

-- 3) 子 path 统一为目录内相对单段(幂等)
UPDATE sys_menu SET path = 'work'              WHERE menu_id = 9101;
UPDATE sys_menu SET path = 'project-detail'     WHERE menu_id = 9110;
UPDATE sys_menu SET path = 'image-annotate'     WHERE menu_id = 9111;
UPDATE sys_menu SET path = 'point-cloud-annotate' WHERE menu_id = 9112;
UPDATE sys_menu SET path = 'sequence-annotate'  WHERE menu_id = 9113;
