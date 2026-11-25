package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 团队 VO
 */
@Data
public class TeamVO implements Serializable {

    private Long id;
    private String teamName;
    private String leaderName;
    /** 成员数 */
    private Long memberCount;
    /** 进行中任务数 */
    private Long runningTaskCount;
    /** 周产出（标注资产量） */
    private Long weeklyOutput;
    /** 质检合格率（0-100） */
    private Long qualityRate;
    private String status;
}
