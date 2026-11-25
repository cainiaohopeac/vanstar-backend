package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 作业 VO（列表/详情）
 */
@Data
public class JobVO implements Serializable {

    private Long id;
    private Long taskId;
    /** 所属项目ID（label_job.project_id，全局属性等项目级配置按此隔离） */
    private Long projectId;
    private String jobNo;
    private String projectName;
    private String taskName;
    /** 作业承接的数据批次ID（任务按批次下发时记录） */
    private Long batchId;
    /** 作业承接的批次名称 */
    private String batchName;
    private String dataType;
    private Long assigneeId;
    private Long reviewerId;
    private String assigneeName;
    private String reviewerName;
    private Integer assetCount;
    private Integer progress;
    private Integer rejectCount;
    private String status;
}
