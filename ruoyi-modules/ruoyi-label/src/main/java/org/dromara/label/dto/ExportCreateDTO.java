package org.dromara.label.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 创建导出任务请求
 */
@Data
public class ExportCreateDTO implements Serializable {

    /** 项目ID（优先；与 projectName 二选一） */
    private Long projectId;
    /** 项目名称（兼容旧版按名称解析，projectId 缺省时使用） */
    private String projectName;
    /** 导出批次ID（导出粒度=批次：批次内全部条目通过全部审核节点方可导出） */
    private Long batchId;
    /** 导出批次ID列表（nuScenes 多批次合并导出：多个批次合并为单一 nuScenes 数据集；与 batchId 二选一，batchIds 优先） */
    private List<Long> batchIds;
    /** 导出格式：JSON（平台原生，含全部几何类型）/ KITTI（label_2 标签，仅框类对象）/ NUSCENES（原格式还原）；缺省 JSON */
    private String format;
    /** @deprecated 导出范围已固定为项目全部合格数据（通过全部审核节点的条目），该参数不再使用 */
    @Deprecated
    private Long assetCount;
}
