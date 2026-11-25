package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 任务条目状态统计 VO（任务详情页「总览 / 待分配 / 已跳过 / 未修订 / 已放弃」页签计数）。
 *
 * <p>按条目标注状态（label_status）分组统计；未修订页签包含
 * 未修订(unrevised)/修订中(revising)/已修订(revised) 三种返修态。</p>
 */
@Data
public class TaskEntrySummaryVO implements Serializable {

    /** 条目总数 */
    private Long total = 0L;
    /** 待分配 */
    private Long pending = 0L;
    /** 标注中 */
    private Long labeling = 0L;
    /** 已标注（首次提交完成，待审核） */
    private Long labeled = 0L;
    /** 未修订（审核不通过，待领取修订） */
    private Long unrevised = 0L;
    /** 修订中 */
    private Long revising = 0L;
    /** 已修订（待复审） */
    private Long revised = 0L;
    /** 已跳过 */
    private Long skipped = 0L;
    /** 已放弃（团队内可见，仅放弃人可恢复） */
    private Long abandoned = 0L;

    // ---------------- 节点状态统计（按工作流节点切换时填充） ----------------

    /** 当前查看的节点 */
    private String nodeKey;
    /** 池子：未领取 */
    private Long nodePending = 0L;
    /** 待办：已领取 */
    private Long nodeClaimed = 0L;
    /** 已跳过 */
    private Long nodeSkipped = 0L;
    /** 已放弃 */
    private Long nodeAbandoned = 0L;
    /** 已完成 */
    private Long nodeDone = 0L;
}
