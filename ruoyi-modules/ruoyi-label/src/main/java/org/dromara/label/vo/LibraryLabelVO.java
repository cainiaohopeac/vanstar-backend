package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 标签库·标签 VO
 */
@Data
public class LibraryLabelVO implements Serializable {

    private Long id;

    /** 所属标签集ID */
    private Long setId;

    /** 所属标签集名称 */
    private String setName;

    /** 适用场景（随集带出）：image / point_cloud / text */
    private String scene;

    /** 标签名称 */
    private String labelName;

    /** 显示颜色 */
    private String color;

    /** 标注工具（视觉场景），空=不限 */
    private String toolType;

    /** 文本节点类型（文字场景）：entity/relation/classification */
    private String nodeType;

    /** 父标签ID（文字场景层级；NULL=顶层，引入复制时据此重建层级） */
    private Long parentId;

    /** 3D框初始尺寸-长（米） */
    private Double defaultSizeL;
    /** 3D框初始尺寸-宽（米） */
    private Double defaultSizeW;
    /** 3D框初始尺寸-高（米） */
    private Double defaultSizeH;

    /** 刚体标识（cuboid 生效） */
    private Boolean isRigid;

    /** 框内最少点云数（cuboid 生效） */
    private Integer minInsidePoints;

    /** 关键点模板 JSON 文本（keypoint 生效） */
    private String keypointTemplate;

    /** 排序 */
    private Integer sortOrder;
}
