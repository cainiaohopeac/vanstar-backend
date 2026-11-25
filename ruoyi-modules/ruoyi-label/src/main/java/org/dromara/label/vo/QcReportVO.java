package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 质检报告 VO
 */
@Data
public class QcReportVO implements Serializable {

    private Long id;
    private String projectName;
    private String taskName;
    private Integer sampleCount;
    private Integer passCount;
    private Integer rejectCount;
    /** 通过率（0-100 整数） */
    private Integer passRate;
    /** 问题汇总（JSON 文本，可空） */
    private String problemSummary;
}
