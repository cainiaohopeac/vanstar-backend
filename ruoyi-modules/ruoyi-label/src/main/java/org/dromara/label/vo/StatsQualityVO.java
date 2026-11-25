package org.dromara.label.vo;

import java.io.Serializable;
import lombok.Data;

/**
 * 成员质量指标：一次通过率、驳回率、返修次数。
 */
@Data
public class StatsQualityVO implements Serializable {

    private Long memberId;

    private String memberName;

    /** 完成条目数。 */
    private Long submittedCount;

    /** 已审核条目数（review_status 为 pass/reject）。 */
    private Long reviewedCount;

    private Long passedCount;

    private Long rejectedCount;

    /** 一次通过条目数（审核通过且从未被驳回）。 */
    private Long firstPassCount;

    /** 一次通过率（%）。 */
    private Double firstPassRate;

    /** 驳回率（%）。 */
    private Double rejectRate;

    /** 返修次数合计（reject_count 累加）。 */
    private Long reworkCount;
}
