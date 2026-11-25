package org.dromara.label.vo;

import java.io.Serializable;
import lombok.Data;

/**
 * 成员条目工作量（柱状图 / 排行榜）。
 */
@Data
public class StatsMemberEntryVO implements Serializable {

    private Long memberId;

    private String memberName;

    /** 已完成条目数（统计主值）。 */
    private Long doneCount;

    /** 当前处理中（领取未完成）条目数，仅作辅助展示。 */
    private Long processingCount;

    /** 跳过条目数。 */
    private Long skippedCount;

    /** 放弃条目数。 */
    private Long abandonedCount;

    /** 平均单条目耗时（秒），仅统计 claim_time 与 done_time 均存在的记录。 */
    private Long avgSeconds;
}
