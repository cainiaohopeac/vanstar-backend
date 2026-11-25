package org.dromara.label.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 创建/更新任务请求
 */
@Data
public class TaskSaveDTO implements Serializable {

    /** 更新时传入任务ID */
    private Long id;
    /** 项目ID（优先使用；前端下拉框已改为按ID选择） */
    private Long projectId;
    /** 项目名称（兼容旧前端，未传项目ID时按名称解析） */
    private String projectName;
    private String taskName;
    /** 团队名称（兼容旧前端，未传 teamIds 时按名称解析并自动创建） */
    private String teamName;
    /** 下发团队ID列表（可多选，取值来自项目绑定的团队池） */
    private List<Long> teamIds;
    /** 下发的数据批次ID列表（可多选，必须是该项目下的批次） */
    private List<Long> batchIds;
    /** 数据类型（image/point_cloud） */
    private String dataType;
    private Integer assetCount;
    /** 是否引入预标注（1 开启 / 0 或空 关闭）：开启后标注工作台把预标注框注入画布，可删改、首次保存转正 */
    private Integer preAnnotationEnabled;
}
