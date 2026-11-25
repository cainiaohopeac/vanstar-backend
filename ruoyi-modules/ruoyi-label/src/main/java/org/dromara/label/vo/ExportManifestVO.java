package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 导出下载清单 VO
 */
@Data
public class ExportManifestVO implements Serializable {

    private Long projectId;
    private String projectName;
    /** 导出批次ID（批次粒度导出；为空=项目全量） */
    private Long batchId;
    /** 导出批次ID列表（nuScenes 多批次合并导出；非空时整包按单一 nuScenes 数据集输出） */
    private List<Long> batchIds;
    /** 批次名称 */
    private String batchName;
    private String format;
    private long assetCount;
    private long objectCount;
    /** mask 几何对象数 */
    private long maskCount;
    private List<ExportAssetVO> assets;
    private List<ExportAnnotationVO> annotations;
}
