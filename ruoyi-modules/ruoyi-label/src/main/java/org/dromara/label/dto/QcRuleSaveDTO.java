package org.dromara.label.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 保存质检规则请求
 */
@Data
public class QcRuleSaveDTO implements Serializable {

    /** 更新时传入规则ID */
    private Long id;
    private String projectName;
    private String taskName;
    /** 抽样率（默认 20） */
    private Number sampleRate;
    private Integer minSampleCount;
    private String status;
}
