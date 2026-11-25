package org.dromara.label.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 分配作业请求
 */
@Data
public class JobAssignDTO implements Serializable {

    /** 标注人员名称（可空，缺省回落默认人员） */
    private String assigneeName;
    /** 审核人员名称（可空，缺省回落默认人员） */
    private String reviewerName;
}
