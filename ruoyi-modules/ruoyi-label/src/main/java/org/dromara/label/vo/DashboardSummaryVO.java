package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 看板首页汇总 VO
 */
@Data
public class DashboardSummaryVO implements Serializable {

    private Long projectCount;
    private Long runningProjectCount;
    private Long assetCount;
    private Long completedAssetCount;
    private Long jobCount;
    private Long pendingReviewCount;
    /** 完成率（0-100） */
    private Integer completionRate;
    /** 质检通过率（0-100） */
    private Integer qualityRate;
}
