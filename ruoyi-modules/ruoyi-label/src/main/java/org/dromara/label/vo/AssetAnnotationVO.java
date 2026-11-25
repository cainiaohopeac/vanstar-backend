package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 资产维度标注 VO（无标注记录时 objects 为空列表）
 */
@Data
public class AssetAnnotationVO implements Serializable {

    private Long assetId;
    private Long jobId;
    private JobVO job;
    private String mediaType;
    private List<WorkAssetVO> assets;
    private WorkAssetVO activeAsset;
    private List<WorkClassVO> classes;
    private List<WorkObjectVO> objects;
}
