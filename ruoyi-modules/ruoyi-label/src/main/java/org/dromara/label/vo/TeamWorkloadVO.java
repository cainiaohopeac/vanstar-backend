package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 团队工作量统计 VO
 */
@Data
public class TeamWorkloadVO implements Serializable {

    private String name;
    /** 累计标注量 */
    private Long total;
    /** 今日标注量 */
    private Long today;
    /** 本周标注量 */
    private Long week;
    /** 审核量 */
    private Long review;
}
