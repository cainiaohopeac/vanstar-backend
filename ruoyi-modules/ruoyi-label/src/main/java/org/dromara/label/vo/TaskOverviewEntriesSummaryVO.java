package org.dromara.label.vo;

import java.io.Serializable;
import lombok.Data;

/**
 * 任务详情「总览」节点状态统计 VO。
 *
 * <p>total = 该任务该批次下全部条目数（含无节点记录的条目）；
 * 各状态桶按最新一条 label_entry_node 记录统计，
 * 无节点记录或最新记录为 pending 均计入 pending（未领取）。</p>
 */
@Data
public class TaskOverviewEntriesSummaryVO implements Serializable {

    /** 总览总条数（全部条目，含未领取）。 */
    private Long total = 0L;
    /** 待领取：无节点记录或最新节点记录为 pending。 */
    private Long pending = 0L;
    /** 待办：已领取且处理中。 */
    private Long claimed = 0L;
    /** 已跳过。 */
    private Long skipped = 0L;
    /** 已放弃。 */
    private Long abandoned = 0L;
    /** 已完成。 */
    private Long done = 0L;
}