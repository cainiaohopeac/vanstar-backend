package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 任务 VO（列表/详情）
 */
@Data
public class TaskVO implements Serializable {

    private Long id;
    private String taskNo;
    private String taskName;
    private Long projectId;
    private String projectCode;
    private String projectName;
    private String dataType;
    /** 创建人名称 */
    private String creatorName;
    /** 主团队名（多团队时列表展示以 teamNames 为准） */
    private String teamName;
    /** 下发团队ID列表 */
    private List<Long> teamIds;
    /** 下发团队名称列表 */
    private List<String> teamNames;
    /** 下发的数据批次ID列表 */
    private List<Long> batchIds;
    /** 下发的数据批次名称列表 */
    private List<String> batchNames;
    private Integer assetCount;
    private Integer completedCount;
    private String status;
    /** 是否引入预标注（0 关闭 / 1 开启） */
    private Integer preAnnotationEnabled;
    /** 任务当前所处工作流节点标识（label / review_N / qualified） */
    private String currentNodeKey;
}
