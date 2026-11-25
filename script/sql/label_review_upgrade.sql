-- ======================================================================
-- 标注平台「条目级双状态机（标注状态 + 审核状态）+ 字典落库」增量升级脚本
--
-- 需求背景（业务确认结论）：
--   1) 条目标注状态与审核状态是「两个互不影响」的维度：
--      · 审核通过不再修改标注状态（已标注仍为已标注、已修订仍为已修订）；
--        「是否通过」由审核状态 review_status=passed 表达。
--      · 审核可多轮：审核不通过 → 未修订 → 领取修订 → 已修订 → 复审 → …
--   2) 审核需要「领取」：已标注/已修订之后由审核人领取，领取即进入 审核中/复审中。
--   3) 「跳过」是标注状态：标注中发现数据有问题/过于复杂可先跳过，进入「已跳过」，
--      再由管理员分配给别人（回到 标注中）。
--   4) 状态统一走字典，前端不再硬编码；字典落库到 sys_dict_type / sys_dict_data。
--
-- 条目标注状态 label_status（字典 label_entry_status）：
--   pending 待分配 / labeling 标注中 / labeled 已标注 /
--   unrevised 未修订 / revising 修订中 / revised 已修订 / skipped 已跳过
-- 条目审核状态 review_status（字典 label_review_status）：
--   none 未审核 / reviewing 审核中 / rejected 审核不通过 /
--   re_reviewing 复审中 / re_rejected 复审不通过 / passed 已通过
--
-- 数据库：MySQL 8.x（ry-vue 标准库，sys_dict_* 为 RuoYi-Plus 标准结构）
-- 幂等性：字段/索引按 information_schema 判断后新增；字典按 dict_type 判重；
--         可重复执行；存量数据按旧值一次性映射回填（重复执行结果一致）。
-- ======================================================================


-- ######################################################################
-- 第一部分：label_asset 新增审核维度字段
-- ######################################################################

-- 1.1 条目审核状态（none/reviewing/rejected/re_reviewing/re_rejected/passed）
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_asset' AND COLUMN_NAME = 'review_status') = 0,
  'ALTER TABLE `label_asset` ADD COLUMN `review_status` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT ''none'' COMMENT ''条目审核状态：none/reviewing/rejected/re_reviewing/re_rejected/passed''',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 1.2 复审轮次（每领取一次审核 +1）
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_asset' AND COLUMN_NAME = 'review_round') = 0,
  'ALTER TABLE `label_asset` ADD COLUMN `review_round` int(11) DEFAULT 0 COMMENT ''审核轮次（领取一次+1）''',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 1.3 最近审核时间
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_asset' AND COLUMN_NAME = 'review_time') = 0,
  'ALTER TABLE `label_asset` ADD COLUMN `review_time` datetime DEFAULT NULL COMMENT ''最近审核时间''',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 1.4 审核维度索引（审核工作台按 审核状态 领取）
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_asset' AND INDEX_NAME = 'idx_asset_review') = 0,
  'ALTER TABLE `label_asset` ADD INDEX `idx_asset_review` (`task_id`, `review_status`, `label_status`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ######################################################################
-- 第二部分：存量 label_status 旧值 → 新值映射（一次性、幂等）
-- ######################################################################

-- 2.1 旧 approved（验收通过）：标注状态回到「已标注」，审核状态记为「已通过」
UPDATE `label_asset`
SET `review_status` = 'passed', `label_status` = 'labeled'
WHERE `label_status` = 'approved' AND `deleted` = 0;

-- 2.2 旧 submitted（待审核）：标注状态 → 已标注，审核状态保持 未审核
UPDATE `label_asset`
SET `review_status` = 'none', `label_status` = 'labeled'
WHERE `label_status` = 'submitted' AND `deleted` = 0;

-- 2.3 旧 rejected（退回/已废弃）：标注状态 → 未修订，审核状态 → 审核不通过
UPDATE `label_asset`
SET `review_status` = 'rejected', `label_status` = 'unrevised'
WHERE `label_status` = 'rejected' AND `deleted` = 0;

-- 2.4 其它存量值兜底：审核状态为空时补 none
UPDATE `label_asset`
SET `review_status` = 'none'
WHERE (`review_status` IS NULL OR `review_status` = '') AND `deleted` = 0;


-- ######################################################################
-- 第三部分：字典落库（sys_dict_type / sys_dict_data）
-- 说明：字典的「表格回显样式」存入 list_class，供前端 <dict-tag> 直接渲染颜色。
-- ######################################################################

-- ----------------------------------------------------------------------
-- 3.1 字典类型
-- ----------------------------------------------------------------------
INSERT INTO `sys_dict_type`(dict_id, tenant_id, dict_name, dict_type, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9001, '000000', '条目标注状态', 'label_entry_status', 103, 1, sysdate(), NULL, NULL, '标注平台条目标注状态（pending/labeling/labeled/unrevised/revising/revised/skipped）'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_type` WHERE tenant_id = '000000' AND dict_type = 'label_entry_status');

INSERT INTO `sys_dict_type`(dict_id, tenant_id, dict_name, dict_type, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9002, '000000', '条目审核状态', 'label_review_status', 103, 1, sysdate(), NULL, NULL, '标注平台条目审核状态（none/reviewing/rejected/re_reviewing/re_rejected/passed）'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_type` WHERE tenant_id = '000000' AND dict_type = 'label_review_status');

INSERT INTO `sys_dict_type`(dict_id, tenant_id, dict_name, dict_type, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9003, '000000', '标注任务状态', 'label_task_status', 103, 1, sysdate(), NULL, NULL, '标注平台任务状态'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_type` WHERE tenant_id = '000000' AND dict_type = 'label_task_status');

INSERT INTO `sys_dict_type`(dict_id, tenant_id, dict_name, dict_type, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9004, '000000', '标注作业状态', 'label_job_status', 103, 1, sysdate(), NULL, NULL, '标注平台作业(job)状态'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_type` WHERE tenant_id = '000000' AND dict_type = 'label_job_status');


-- ----------------------------------------------------------------------
-- 3.2 条目标注状态字典数据
-- ----------------------------------------------------------------------
INSERT INTO `sys_dict_data`(dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9011, '000000', 1, '待分配', 'pending',   'label_entry_status', '', 'info',    'N', 103, 1, sysdate(), NULL, NULL, '未分配给标注员'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE dict_code = 9011);
INSERT INTO `sys_dict_data`(dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9012, '000000', 2, '标注中', 'labeling',  'label_entry_status', '', 'primary', 'N', 103, 1, sysdate(), NULL, NULL, '已领取/已分配，正在标注'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE dict_code = 9012);
INSERT INTO `sys_dict_data`(dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9013, '000000', 3, '已标注', 'labeled',   'label_entry_status', '', 'warning', 'N', 103, 1, sysdate(), NULL, NULL, '首次提交完成，待审核'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE dict_code = 9013);
INSERT INTO `sys_dict_data`(dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9014, '000000', 4, '未修订', 'unrevised', 'label_entry_status', '', 'danger',  'N', 103, 1, sysdate(), NULL, NULL, '审核不通过，待领取修订'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE dict_code = 9014);
INSERT INTO `sys_dict_data`(dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9015, '000000', 5, '修订中', 'revising',  'label_entry_status', '', 'primary', 'N', 103, 1, sysdate(), NULL, NULL, '已领取修订，正在修订'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE dict_code = 9015);
INSERT INTO `sys_dict_data`(dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9016, '000000', 6, '已修订', 'revised',   'label_entry_status', '', 'warning', 'N', 103, 1, sysdate(), NULL, NULL, '修订后提交完成，待复审'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE dict_code = 9016);
INSERT INTO `sys_dict_data`(dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9017, '000000', 7, '已跳过', 'skipped',   'label_entry_status', '', 'info',    'N', 103, 1, sysdate(), NULL, NULL, '标注中跳过，可由管理员重新分配'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE dict_code = 9017);


-- ----------------------------------------------------------------------
-- 3.3 条目审核状态字典数据
-- ----------------------------------------------------------------------
INSERT INTO `sys_dict_data`(dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9021, '000000', 1, '未审核', 'none',        'label_review_status', '', 'info',    'N', 103, 1, sysdate(), NULL, NULL, '尚无人领取'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE dict_code = 9021);
INSERT INTO `sys_dict_data`(dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9022, '000000', 2, '审核中', 'reviewing',   'label_review_status', '', 'primary', 'N', 103, 1, sysdate(), NULL, NULL, '初审已领取'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE dict_code = 9022);
INSERT INTO `sys_dict_data`(dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9023, '000000', 3, '审核不通过', 'rejected',    'label_review_status', '', 'danger',  'N', 103, 1, sysdate(), NULL, NULL, '初审驳回'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE dict_code = 9023);
INSERT INTO `sys_dict_data`(dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9024, '000000', 4, '复审中', 're_reviewing', 'label_review_status', '', 'primary', 'N', 103, 1, sysdate(), NULL, NULL, '复审已领取（第2轮起）'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE dict_code = 9024);
INSERT INTO `sys_dict_data`(dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9025, '000000', 5, '复审不通过', 're_rejected', 'label_review_status', '', 'danger',  'N', 103, 1, sysdate(), NULL, NULL, '复审驳回'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE dict_code = 9025);
INSERT INTO `sys_dict_data`(dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9026, '000000', 6, '已通过', 'passed',      'label_review_status', '', 'success', 'N', 103, 1, sysdate(), NULL, NULL, '终态：初审或复审通过'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE dict_code = 9026);


-- ----------------------------------------------------------------------
-- 3.4 任务状态字典数据
-- ----------------------------------------------------------------------
INSERT INTO `sys_dict_data`(dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9031, '000000', 1, '待分配', 'pending_assign',    'label_task_status', '', 'info',    'N', 103, 1, sysdate(), NULL, NULL, '待分配'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE dict_code = 9031);
INSERT INTO `sys_dict_data`(dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9032, '000000', 2, '待标注', 'pending_label',     'label_task_status', '', 'info',    'N', 103, 1, sysdate(), NULL, NULL, '待标注'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE dict_code = 9032);
INSERT INTO `sys_dict_data`(dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9033, '000000', 3, '标注中', 'labeling',          'label_task_status', '', 'primary', 'N', 103, 1, sysdate(), NULL, NULL, '标注中'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE dict_code = 9033);
INSERT INTO `sys_dict_data`(dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9034, '000000', 4, '待审核', 'pending_review',    'label_task_status', '', 'warning', 'N', 103, 1, sysdate(), NULL, NULL, '待审核'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE dict_code = 9034);
INSERT INTO `sys_dict_data`(dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9035, '000000', 5, '已退回', 'review_rejected',   'label_task_status', '', 'danger',  'N', 103, 1, sysdate(), NULL, NULL, '已退回'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE dict_code = 9035);
INSERT INTO `sys_dict_data`(dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9036, '000000', 6, '待验收', 'pending_acceptance','label_task_status', '', 'warning', 'N', 103, 1, sysdate(), NULL, NULL, '待验收'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE dict_code = 9036);
INSERT INTO `sys_dict_data`(dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9037, '000000', 7, '已完成', 'completed',         'label_task_status', '', 'success', 'N', 103, 1, sysdate(), NULL, NULL, '已完成'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE dict_code = 9037);


-- ----------------------------------------------------------------------
-- 3.5 作业状态字典数据
-- ----------------------------------------------------------------------
INSERT INTO `sys_dict_data`(dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9041, '000000', 1, '待分配', 'pending_assign',    'label_job_status', '', 'info',    'N', 103, 1, sysdate(), NULL, NULL, '待分配'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE dict_code = 9041);
INSERT INTO `sys_dict_data`(dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9042, '000000', 2, '待标注', 'pending_label',     'label_job_status', '', 'info',    'N', 103, 1, sysdate(), NULL, NULL, '待标注'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE dict_code = 9042);
INSERT INTO `sys_dict_data`(dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9043, '000000', 3, '标注中', 'labeling',          'label_job_status', '', 'primary', 'N', 103, 1, sysdate(), NULL, NULL, '标注中'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE dict_code = 9043);
INSERT INTO `sys_dict_data`(dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9044, '000000', 4, '待审核', 'pending_review',    'label_job_status', '', 'warning', 'N', 103, 1, sysdate(), NULL, NULL, '待审核'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE dict_code = 9044);
INSERT INTO `sys_dict_data`(dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9045, '000000', 5, '已退回', 'review_rejected',   'label_job_status', '', 'danger',  'N', 103, 1, sysdate(), NULL, NULL, '已退回'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE dict_code = 9045);
INSERT INTO `sys_dict_data`(dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9046, '000000', 6, '待验收', 'pending_acceptance','label_job_status', '', 'warning', 'N', 103, 1, sysdate(), NULL, NULL, '待验收'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE dict_code = 9046);
INSERT INTO `sys_dict_data`(dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 9047, '000000', 7, '已完成', 'completed',         'label_job_status', '', 'success', 'N', 103, 1, sysdate(), NULL, NULL, '已完成'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE dict_code = 9047);


-- ######################################################################
-- 第四部分：执行结果自检
-- ######################################################################
SELECT 'label_asset.review_status' AS chk, COUNT(*) AS ok FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_asset' AND COLUMN_NAME = 'review_status'
UNION ALL
SELECT 'label_asset.review_round',  COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_asset' AND COLUMN_NAME = 'review_round'
UNION ALL
SELECT 'label_asset.review_time',   COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'label_asset' AND COLUMN_NAME = 'review_time'
UNION ALL
SELECT 'dict label_entry_status',   COUNT(*) FROM sys_dict_type WHERE tenant_id = '000000' AND dict_type = 'label_entry_status'
UNION ALL
SELECT 'dict label_review_status',  COUNT(*) FROM sys_dict_type WHERE tenant_id = '000000' AND dict_type = 'label_review_status'
UNION ALL
SELECT 'dict label_task_status',    COUNT(*) FROM sys_dict_type WHERE tenant_id = '000000' AND dict_type = 'label_task_status'
UNION ALL
SELECT 'dict label_job_status',     COUNT(*) FROM sys_dict_type WHERE tenant_id = '000000' AND dict_type = 'label_job_status';
