package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 批次下发占用 VO：描述某数据批次已被哪次任务下发。
 * 业务规则为「同一批次只允许下发一次」，任务创建时据此校验，前端据此禁用已下发批次。
 */
@Data
public class BatchOccupancyVO implements Serializable {

    /** 数据批次ID */
    private Long batchId;
    /** 批次名称（为空时前端用批次号兜底） */
    private String batchName;
    /** 占用该批次的任务ID */
    private Long taskId;
    /** 占用该批次的任务编号 */
    private String taskNo;
    /** 占用该批次的任务名称 */
    private String taskName;
}
