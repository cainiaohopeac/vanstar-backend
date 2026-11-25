package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 质检规则 VO
 */
@Data
public class QcRuleVO implements Serializable {

    private Long id;
    private Long projectId;
    private Long taskId;
    private String projectName;
    private String taskName;
    /** 抽样率（如 20） */
    private Number sampleRate;
    private Integer minSampleCount;
    private String status;
}
