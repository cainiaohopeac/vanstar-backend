package org.dromara.label.vo;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * 统计分析筛选项：节点、团队、项目、任务、成员。
 */
@Data
public class StatsFilterVO implements Serializable {

    private List<StatsNodeOptionVO> nodes;

    private List<StatsOptionVO> teams;

    private List<StatsOptionVO> projects;

    private List<StatsOptionVO> tasks;

    private List<StatsOptionVO> members;

    /** 条目下拉项（需指定项目；配合标签工作量的条目细筛）。 */
    private List<StatsOptionVO> entries;

    /** 批次下拉项（需指定项目；配合帧工作量/每日条目数的批次细筛）。 */
    private List<StatsOptionVO> batches;

    /** 默认选中节点：优先 label 类型节点。 */
    private String defaultNodeKey;
}
