package org.dromara.label.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 标签库·标签创建/编辑请求
 */
@Data
public class LibraryLabelDTO implements Serializable {

    /** 标签ID（更新时必传，创建时不传） */
    private Long id;

    /** 所属标签集ID（创建必传） */
    private Long setId;

    /** 标签名称 */
    private String labelName;

    /** 显示颜色 */
    private String color;

    /**
     * 标注工具（bbox/polygon/brush/box3d/cuboid/polyline/keypoint/segmentation），
     * 仅视觉场景（image/point_cloud）生效，空=不限。
     */
    private String toolType;

    /**
     * 文本节点类型：entity/relation/classification，仅文字场景（text）生效。
     */
    private String nodeType;

    /** 3D框初始尺寸-长（米，size.x）；null 表示不预设 */
    private Double defaultSizeL;
    /** 3D框初始尺寸-宽（米，size.y）；null 表示不预设 */
    private Double defaultSizeW;
    /** 3D框初始尺寸-高（米，size.z）；null 表示不预设 */
    private Double defaultSizeH;

    /** 刚体标识（仅 tool_type=cuboid 生效）：同轨迹尺寸自动同步 */
    private Boolean isRigid;

    /** 框内最少点云数（仅 tool_type=cuboid 生效），0/null=不限 */
    private Integer minInsidePoints;

    /**
     * 关键点模板（仅 tool_type=keypoint）：
     * { "points": ["左眼", "右眼", ...], "edges": [[0,1],[1,2], ...] }
     */
    private Object keypointTemplate;

    /** 排序值（缺省 0） */
    private Integer sortOrder;
}
