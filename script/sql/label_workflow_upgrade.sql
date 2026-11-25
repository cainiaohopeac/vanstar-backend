-- ======================================================================
-- 标注平台「项目级动态工作流节点」增量升级脚本
--
-- 需求背景（业务确认结论）：
--   1) 项目内标注流转为「原始数据 → 标注 → [动态审核节点 1..N] → 合格数据」，
--      项目之间完全隔离：每个项目一套自己的节点配置，任务互不影响。
--   2) 固定节点：原始数据(start) / 标注(label) / 合格数据(end)，不可删除、不可改 key；
--      动态节点：审核(review)，默认 1 个（review_1），可增删、改名、排序。
--   3) 暂不做「验收」节点：最后一个审核节点全部通过即视为「合格数据」。
--   4) 任务增加 current_node_key 记录当前所处节点；
--      任务被驳回时保持 current_node_key 不变，用任务状态 review_rejected 表达「已退回标注修订」。
--   5) 任务成员归属节点：label_task_member.node_key（不允许为 NULL，默认 'label' 标注节点）；
--      成员只有在任务流转到自己所属节点时才能领取标注 / 审核；
--      管理员（超管、任务创建者、团队负责人、任务管理员）不受节点限制。
--
-- 数据库：MySQL 8.x（ry-vue 标准库，sys_dict_* 为 RuoYi-Plus 标准结构）
-- 幂等性：表按 CREATE TABLE IF NOT EXISTS；字段/索引按 information_schema 判断后新增；
--         字典按 dict_type / dict_code 判重；存量数据按旧值一次性回填（重复执行结果一致）。
-- ======================================================================


-- ######################################################################
-- 第一部分：新表 label_workflow_node（项目工作流节点配置）
-- ######################################################################

CREATE TABLE IF NOT EXISTS `label_workflow_node` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '节点ID',
  `project_id` bigint(20) NOT NULL COMMENT '所属项目ID（项目隔离）',
  `node_key` varchar(64) NOT NULL COMMENT '节点标识（项目内唯一：raw_data/label/review_N/qualified）',
  `node_name` varchar(60) NOT NULL COMMENT '节点名称（可修改）',
  `node_type` varchar(20) NOT NULL COMMENT '节点类型：start 原始数据 / label 标注 / review 审核 / end 合格数据',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '流转顺序（升序）',
  `remark` varchar(255) DEFAULT NULL COMMENT '节点说明',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) NOT NULL DEFAULT 0 COMMENT '删除标志（0存在 1删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_node` (`project_id`, `node_key`),
  KEY `idx_project_sort` (`project_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='项目工作流节点配置（原始数据→标注→动态审核节点→合格数据）';


-- ######################################################################
-- 第二部分：label_task 增加当前节点字段
-- ######################################################################

-- 2.1 任务当前所处工作流节点
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_task' AND COLUMN_NAME = 'current_node_key') = 0,
  'ALTER TABLE `label_task` ADD COLUMN `current_node_key` varchar(64) NOT NULL DEFAULT ''label'' COMMENT ''任务当前所处工作流节点（label/review_N/qualified）''',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2.2 任务节点索引（按项目/节点筛任务）
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_task' AND INDEX_NAME = 'idx_task_node') = 0,
  'ALTER TABLE `label_task` ADD INDEX `idx_task_node` (`project_id`, `current_node_key`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ######################################################################
-- 第三部分：label_task_member 增加成员归属节点字段
-- ######################################################################

-- 3.1 成员归属节点（不允许 NULL，默认标注节点；存量成员自动归入标注节点）
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_task_member' AND COLUMN_NAME = 'node_key') = 0,
  'ALTER TABLE `label_task_member` ADD COLUMN `node_key` varchar(64) NOT NULL DEFAULT ''label'' COMMENT ''成员归属工作流节点（默认 label 标注节点）''',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3.2 存量成员兜底回填（历史行可能为空字符串）
UPDATE `label_task_member` SET `node_key` = 'label' WHERE `node_key` IS NULL OR `node_key` = '';


-- ######################################################################
-- 第四部分：字典落库（工作流节点类型）
-- ######################################################################

INSERT INTO `sys_dict_type`(dict_id, tenant_id, dict_name, dict_type, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9005, '000000', '工作流节点类型', 'label_flow_node_type', 103, 1, sysdate(), NULL, NULL, '标注平台项目工作流节点类型（start/label/review/end）'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_type` WHERE tenant_id = '000000' AND dict_type = 'label_flow_node_type');

INSERT INTO `sys_dict_data`(dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9061, '000000', 1, '原始数据', 'start',  'label_flow_node_type', '', 'info',    'N', 103, 1, sysdate(), NULL, NULL, '项目数据起点（固定节点）'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE dict_code = 9061);
INSERT INTO `sys_dict_data`(dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9062, '000000', 2, '标注',     'label',  'label_flow_node_type', '', 'primary', 'N', 103, 1, sysdate(), NULL, NULL, '标注员领取并提交标注（固定节点）'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE dict_code = 9062);
INSERT INTO `sys_dict_data`(dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9063, '000000', 3, '审核',     'review', 'label_flow_node_type', '', 'warning', 'N', 103, 1, sysdate(), NULL, NULL, '审核员领取并审核，可驳回（动态节点，可增删）'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE dict_code = 9063);
INSERT INTO `sys_dict_data`(dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9064, '000000', 4, '合格数据', 'end',    'label_flow_node_type', '', 'success', 'N', 103, 1, sysdate(), NULL, NULL, '全部审核节点通过后产出（固定节点）'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE dict_code = 9064);


-- ######################################################################
-- 第五部分：存量项目初始化默认工作流（原始数据→标注→审核→合格数据）
-- ######################################################################

INSERT INTO `label_workflow_node`
  (`project_id`, `node_key`, `node_name`, `node_type`, `sort_order`, `remark`, `create_time`, `update_time`, `created_by`, `updated_by`, `deleted`)
SELECT p.`id`, d.`node_key`, d.`node_name`, d.`node_type`, d.`sort_order`, d.`remark`, NOW(), NOW(), 1, 1, 0
FROM `label_project` p
JOIN (
            SELECT 'raw_data'  AS `node_key`, '原始数据' AS `node_name`, 'start'  AS `node_type`, 10 AS `sort_order`, '项目数据起点' AS `remark`
  UNION ALL SELECT 'label',     '标注',     'label',  20, '标注员领取并提交标注'
  UNION ALL SELECT 'review_1',  '审核',     'review', 30, '审核员领取并审核，可驳回后重新提交'
  UNION ALL SELECT 'qualified', '合格数据', 'end',    40, '全部审核通过后产出合格数据'
) d
WHERE p.`deleted` = 0
  AND NOT EXISTS (SELECT 1 FROM `label_workflow_node` n WHERE n.`project_id` = p.`id` AND n.`deleted` = 0);


-- ######################################################################
-- 第六部分：存量任务回填当前节点（按任务状态映射，一次性、幂等）
-- ######################################################################

-- 6.1 待审核任务 → 项目内第一个审核节点
UPDATE `label_task` t
JOIN `label_workflow_node` n
  ON n.`project_id` = t.`project_id`
 AND n.`node_type` = 'review'
 AND n.`deleted` = 0
SET t.`current_node_key` = n.`node_key`
WHERE t.`deleted` = 0
  AND t.`status` = 'pending_review'
  AND (t.`current_node_key` IS NULL OR t.`current_node_key` IN ('', 'label'))
  AND n.`sort_order` = (SELECT MIN(n2.`sort_order`) FROM `label_workflow_node` n2
                        WHERE n2.`project_id` = t.`project_id` AND n2.`node_type` = 'review' AND n2.`deleted` = 0);

-- 6.2 已完成 / 待验收任务 → 合格数据节点
UPDATE `label_task`
SET `current_node_key` = 'qualified'
WHERE `deleted` = 0
  AND `status` IN ('completed', 'pending_acceptance')
  AND (`current_node_key` IS NULL OR `current_node_key` <> 'qualified');

-- 6.3 其余任务（待分配/待标注/标注中/已退回）→ 标注节点
UPDATE `label_task`
SET `current_node_key` = 'label'
WHERE `deleted` = 0
  AND `status` IN ('pending_assign', 'pending_label', 'labeling', 'review_rejected')
  AND (`current_node_key` IS NULL OR `current_node_key` = '');

-- 6.4 兜底：仍为空的补标注节点
UPDATE `label_task` SET `current_node_key` = 'label' WHERE `deleted` = 0 AND (`current_node_key` IS NULL OR `current_node_key` = '');


-- ######################################################################
-- 第七部分：执行结果自检
-- ######################################################################

SELECT 'table label_workflow_node' AS chk, COUNT(*) AS ok FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_workflow_node'
UNION ALL
SELECT 'label_task.current_node_key', COUNT(*) FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_task' AND COLUMN_NAME = 'current_node_key'
UNION ALL
SELECT 'label_task_member.node_key', COUNT(*) FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_task_member' AND COLUMN_NAME = 'node_key'
UNION ALL
SELECT 'dict label_flow_node_type', COUNT(*) FROM sys_dict_type
 WHERE tenant_id = '000000' AND dict_type = 'label_flow_node_type'
UNION ALL
SELECT 'projects initialized', COUNT(DISTINCT project_id) FROM label_workflow_node WHERE deleted = 0
UNION ALL
SELECT 'tasks with node', COUNT(*) FROM label_task WHERE deleted = 0 AND current_node_key IS NOT NULL AND current_node_key <> '';
