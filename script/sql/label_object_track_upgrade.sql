-- =====================================================================
-- 逐帧（关键帧插值）功能升级脚本
--
-- 设计要点（业界 CVAT / Label Studio 同款「关键帧 + 插值」模型）：
--   1. 中间帧不落库：库里只存「关键帧」，非关键帧由前端按 track 实时插值渲染；
--      因此 label_annotation_object 上凡是带 track_token 的行，天然就是关键帧，
--      无需额外的 is_keyframe 列。
--   2. 向后兼容：没有 track_token 的历史对象仍然「每帧独立」，
--      不参与插值，老数据零迁移。
--   3. 存在范围：一条 track 只支持单一连续区间 [first_asset_id, last_asset_id]
--      （「从下一帧消失」/「取消连续」= 前向截断）；last_asset_id 为 NULL 表示
--      延续到帧序列末帧（对应「末段固定：保持最后关键帧状态」）。
--   4. 空洞 holes_json：中间的帧可「删除此帧」，仅该帧不显示，前后帧不受影响。
--   5. 插值引入了 interpolation 字段占位，当前只实现 linear，
--      为后续 Catmull-Rom 样条（平滑曲线运动）预留扩展。
-- =====================================================================

CREATE TABLE IF NOT EXISTS `label_object_track` (
    `id`            bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    -- 冗余项目维度便于按项目统计/清理；JobVO 不携带该字段，故由 label_job 子查询回填
    `project_id`    bigint(20)   NOT NULL DEFAULT '0' COMMENT '项目ID（由 label_job.project_id 回填）',
    `job_id`        bigint(20)   NOT NULL COMMENT '作业ID',
    `entry_id`      bigint(20)   DEFAULT NULL COMMENT '所属条目ID（逐帧按条目作用域，可空）',
    `track_token`   varchar(80)  NOT NULL COMMENT '轨迹Token（跨帧同一目标标识，与 label_annotation_object.track_token 一致）',
    `class_id`      bigint(20)   DEFAULT NULL COMMENT '标签ID',
    `first_asset_id` bigint(20)  NOT NULL COMMENT '存在范围起始帧（资产ID）',
    `last_asset_id` bigint(20)   DEFAULT NULL COMMENT '存在范围结束帧（资产ID）；NULL=延续到帧序列末帧',
    `holes_json`    json         DEFAULT NULL COMMENT '单帧删除集合（资产ID数组；该帧不显示，其余帧不受影响）',
    -- 虚框区间集合：[{from,to,dir}]，from/to 为资产ID，dir=fwd/bwd
    -- 「从下一帧/上一帧消失」实框转虚框且截止到最近关键帧：区间内帧单端外推显示虚线框，端点关键帧保持实框
    `gaps_json`     json         DEFAULT NULL COMMENT '虚框区间集合（资产ID区间数组）',
    `interpolation` varchar(20)  DEFAULT 'linear' COMMENT '插值方式：linear=线性（位置与长宽均匀变化）',
    `create_time`   datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_by`    bigint(20)   DEFAULT NULL COMMENT '创建人',
    `updated_by`    bigint(20)   DEFAULT NULL COMMENT '更新人',
    `deleted`       tinyint(4)   DEFAULT '0' COMMENT '删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_track_token` (`track_token`),
    KEY `idx_job_entry` (`job_id`, `entry_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='标注对象轨迹表（逐帧关键帧插值的存在范围与空洞）';

-- 关键帧本身即是带 track_token 的 label_annotation_object 行，其所属资产由
-- label_annotation(job_id, asset_id) 隐含确定，因此无需额外的冗余索引。
-- 如需按资产反查轨迹，可结合 label_annotation 的 (job_id, asset_id) 索引。

-- =====================================================================
-- 增量升级：已有环境补加虚框区间列（表已存在时 CREATE TABLE IF NOT EXISTS 不会生效）
-- =====================================================================
ALTER TABLE `label_object_track`
    ADD COLUMN IF NOT EXISTS `gaps_json` json DEFAULT NULL
    COMMENT '虚框区间集合（资产ID区间数组；[{from,to,dir}]，dir=fwd/bwd）'
    AFTER `holes_json`;
