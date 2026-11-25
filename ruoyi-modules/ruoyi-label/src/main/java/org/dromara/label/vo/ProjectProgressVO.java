package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 看板项目任务进度 VO
 */
@Data
public class ProjectProgressVO implements Serializable {

    private String taskNo;
    private String taskName;
    private String projectName;
    private Integer assetCount;
    private Integer completedCount;
    private Integer progress;
}
