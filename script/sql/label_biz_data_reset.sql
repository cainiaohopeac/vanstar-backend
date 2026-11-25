-- ======================================================================
-- 标注平台「业务数据重置」脚本
--
-- 目的：保留系统基础配置与团队配置，清空全部标注业务数据，
--       使平台回到「配置齐全、业务为空」的干净可交付状态。
--
-- 数据库：MySQL 8.x
-- 适用库：label_server（老平台库）与 ry-vue 标准库均可，脚本按
--         information_schema 动态判断表是否存在 —— 不存在自动跳过，
--         不会因某个库缺少框架表而中断。
--
-- 【保留，绝不清理】
--   人员/组织/权限  sys_user、sys_dept、sys_post、sys_role、sys_menu、
--                   sys_user_role、sys_role_menu、sys_role_dept、sys_user_post
--   字典/参数/存储  sys_dict_type、sys_dict_data、sys_config、sys_oss_config
--   租户/客户端/三方 sys_tenant、sys_tenant_package、sys_client、sys_social
--   代码生成配置    gen_table、gen_table_column
--   团队配置        label_team（团队表）、label_team_member（团队成员表）
--   说明：label_team / label_team_member 不带 project_id，是全局团队配置，
--         不随项目清空，因此单列白名单予以保护。
--
-- 【清理，业务数据】
--   ① 项目与标签体系   ② 批次/上传/导入   ③ 数据资产与条目
--   ④ 序列（nuScenes） ⑤ 任务与作业       ⑥ 标注/预标注
--   ⑦ 审核/质检/交付/导出
--   ⑧ 系统运行数据（日志、消息）          ⑨ 框架运行数据（flow/sj）
--   ⑩ 演示与测试数据
--
-- 【执行前务必】
--   1) 备份：mysqldump -h127.0.0.1 -uroot -p label_server > label_server_bak.sql
--   2) 本脚本不可回滚，TRUNCATE 会同时重置 AUTO_INCREMENT。
--   3) 账号需具备 DROP 权限（TRUNCATE 要求）；若仅有 DELETE 权限，
--      见文末「附注 B」的替代写法。
--
-- 【执行方式】
--   mysql -h127.0.0.1 -P3306 -uroot -p label_server < label_biz_data_reset.sql
--   或在 Navicat / DataGrip 中整体执行（客户端需支持 DELIMITER 指令）。
-- ======================================================================


SET FOREIGN_KEY_CHECKS = 0;


-- ======================================================================
-- 第一部分  清空前预览（估算值，仅供确认数据量，不参与清理）
-- ======================================================================
SELECT t.table_name                              AS `待清理表`,
       t.table_rows                              AS `估算行数`,
       t.table_comment                           AS `说明`
  FROM information_schema.tables t
 WHERE t.table_schema = DATABASE()
   AND t.table_type = 'BASE TABLE'
   AND (
        t.table_name LIKE 'label\_%'
     OR t.table_name LIKE 'import\_%'
     OR t.table_name IN ('sys_logininfor','sys_oper_log','sys_operation_log',
                         'sys_notice','sys_notification','sys_oss',
                         'test_demo','test_leave','test_tree')
   )
   AND t.table_name NOT IN ('label_team','label_team_member')
 ORDER BY t.table_name;


-- ======================================================================
-- 第二部分  通用清理过程
--   入参为逗号分隔的表名清单；库中不存在的表自动跳过。
-- ======================================================================
DROP PROCEDURE IF EXISTS `sp_clear_biz_data`;

DELIMITER $$
CREATE PROCEDURE `sp_clear_biz_data`(IN p_tables TEXT)
BEGIN
  DECLARE v_done  INT DEFAULT 0;
  DECLARE v_table VARCHAR(64);
  DECLARE v_log   VARCHAR(2000) DEFAULT '';
  DECLARE v_count INT DEFAULT 0;
  DECLARE v_sql   VARCHAR(255);

  DECLARE cur CURSOR FOR
    SELECT t.table_name
      FROM information_schema.tables t
     WHERE t.table_schema = DATABASE()
       AND t.table_type = 'BASE TABLE'
       AND FIND_IN_SET(t.table_name, p_tables) > 0;

  DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;

  OPEN cur;
  clear_loop: LOOP
    FETCH cur INTO v_table;
    IF v_done = 1 THEN LEAVE clear_loop; END IF;

    SET v_sql = CONCAT('TRUNCATE TABLE `', v_table, '`');
    SET @clear_sql = v_sql;
    PREPARE clear_stmt FROM @clear_sql;
    EXECUTE clear_stmt;
    DEALLOCATE PREPARE clear_stmt;

    SET v_count = v_count + 1;
    SET v_log = CONCAT(v_log, v_table, ' ');
  END LOOP;
  CLOSE cur;

  SELECT CONCAT('已清空 ', v_count, ' 张表：', v_log) AS result;
END$$
DELIMITER ;


-- ======================================================================
-- 第三部分  执行清理（按业务域分组，便于核对）
-- ======================================================================

-- ① 项目与标签体系（标签集/标签挂在项目下，属项目级配置；
--    label_workflow_node 为「项目级」工作流节点配置，随项目清空）
CALL sp_clear_biz_data('label_project,label_project_member,label_project_team,label_class_set,label_class,label_class_attribute,label_workflow_node');

-- ② 批次 / 上传 / 导入
CALL sp_clear_biz_data('label_asset_batch,label_upload,import_record,import_part');

-- ③ 数据资产与数据条目（条目 = 数据集合包）
CALL sp_clear_biz_data('label_data_entry,label_asset');

-- ④ 序列（nuScenes 多传感器多帧：序列/帧/传感器/标定/位姿/传感器数据/目标实例）
CALL sp_clear_biz_data('label_sequence,label_frame,label_sensor,label_calibration,label_ego_pose,label_sensor_data,label_instance');

-- ⑤ 任务与作业（任务、下发范围、成员、收藏、作业及其资产关联）
CALL sp_clear_biz_data('label_task,label_task_team,label_task_batch,label_task_member,label_task_favorite,label_job,label_job_asset');

-- ⑥ 标注 / 预标注 / 复核数据
CALL sp_clear_biz_data('label_annotation,label_annotation_object,label_annotation_error,label_comment,label_pre_annotation,label_pre_annotation_object,label_precheck');

-- ⑦ 审核 / 质检 / 交付 / 导出
--    label_qc_rule（质检规则）挂在 project_id / task_id 下，属任务级配置，一并清空
CALL sp_clear_biz_data('label_review_record,label_qc_report,label_qc_rule,label_delivery_record,label_export_job');

-- ⑧ 系统运行数据（登录/操作日志、公告、站内消息、上传文件记录）
--    如需保留通知公告，请从清单中移除 sys_notice
CALL sp_clear_biz_data('sys_logininfor,sys_oper_log,sys_operation_log,sys_notice,sys_notification,sys_oss');

-- ⑨ 框架运行数据（流程实例与待办、调度执行与重试记录；框架「定义/配置」不动）
CALL sp_clear_biz_data('flow_instance,flow_instance_biz_ext,flow_task,flow_his_task');
CALL sp_clear_biz_data('sj_job_task,sj_job_task_batch,sj_job_summary,sj_job_log_message,sj_distributed_lock,sj_retry,sj_retry_task,sj_retry_summary,sj_retry_task_log_message,sj_retry_dead_letter');

-- ⑩ 演示与测试数据
CALL sp_clear_biz_data('test_demo,test_leave,test_tree');


-- ======================================================================
-- 第四部分  收尾与结果自检
-- ======================================================================
DROP PROCEDURE IF EXISTS `sp_clear_biz_data`;

SET FOREIGN_KEY_CHECKS = 1;

-- 4.1 待清理清单剩余行数（应全部为 0 或该表不存在）
SELECT t.table_name AS `表名`, t.table_rows AS `估算剩余行数`
  FROM information_schema.tables t
 WHERE t.table_schema = DATABASE()
   AND t.table_type = 'BASE TABLE'
   AND (
        t.table_name LIKE 'label\_%'
     OR t.table_name LIKE 'import\_%'
     OR t.table_name IN ('sys_logininfor','sys_oper_log','sys_operation_log',
                         'sys_notice','sys_notification','sys_oss',
                         'flow_instance','flow_instance_biz_ext','flow_task','flow_his_task',
                         'sj_job_task','sj_job_task_batch','sj_job_summary','sj_job_log_message',
                         'sj_distributed_lock','sj_retry','sj_retry_task','sj_retry_summary',
                         'sj_retry_task_log_message','sj_retry_dead_letter',
                         'test_demo','test_leave','test_tree')
   )
   AND t.table_name NOT IN ('label_team','label_team_member')
 ORDER BY t.table_name;

-- 4.2 保留项确认（这几张表**不应**为 0，用于确认未被误清）
SELECT 'sys_user'           AS `保留表`, COUNT(*) AS `行数` FROM `sys_user`
UNION ALL SELECT 'sys_role',          COUNT(*) FROM `sys_role`
UNION ALL SELECT 'sys_menu',          COUNT(*) FROM `sys_menu`
UNION ALL SELECT 'label_team',        COUNT(*) FROM `label_team`
UNION ALL SELECT 'label_team_member', COUNT(*) FROM `label_team_member`;


-- ======================================================================
-- 附注 A  MinIO 对象与 Redis 缓存
--
--   本脚本只清数据库，不处理对象存储：
--     * label_asset.file_url / label_upload / label_sensor_data 等列存的是
--       MinIO 对象路径，清库后这些对象会变成孤儿对象（占空间但不报错）。
--       如需彻底清理，按原路径前缀整体删除 bucket 下的 imports/ 与 assets/ 目录。
--     * label_asset_batch 关联的原图/点云 zip 同理。
--
--   缓存：sys_config / sys_dict_* 属于保留表，其 Redis 缓存仍然有效，
--   无需清理；但建议清一次业务相关缓存键（如 label:* 前缀）以避免
--   残留的标签体系/项目缓存与新状态不一致。
--
-- 附注 B  无 DROP 权限时的替代写法
--
--   若账号不允许 TRUNCATE，把存储过程中的
--     SET v_sql = CONCAT('TRUNCATE TABLE `', v_table, '`');
--   改为
--     SET v_sql = CONCAT('DELETE FROM `', v_table, '`');
--   并在脚本开头加 SET SQL_SAFE_UPDATES = 0;
--   （DELETE 不重置自增，如需重置需另行 ALTER TABLE ... AUTO_INCREMENT = 1）
-- ======================================================================
