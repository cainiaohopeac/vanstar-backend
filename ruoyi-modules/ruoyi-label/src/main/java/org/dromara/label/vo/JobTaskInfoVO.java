package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 标注工作台「数据明细」任务/批次概要 VO（随作业标注取数一并返回）
 */
@Data
public class JobTaskInfoVO implements Serializable {

    /** 任务编号（TASK-YYYY-xxx，前端条目信息复制用） */
    private String taskNo;

    /** 任务名称 */
    private String taskName;

    /** 任务数据类型（image/point_cloud/...） */
    private String dataType;

    /** 作业归属批次名称（无批次时为 null，前端显示"-"） */
    private String batchName;

    /** 条目数量：作业有批次时为该批次条目总数，否则为任务条目总数 */
    private Long batchEntryCount;

    /** 条目类型（作业数据类型 image/point_cloud/...） */
    private String mediaType;
}
