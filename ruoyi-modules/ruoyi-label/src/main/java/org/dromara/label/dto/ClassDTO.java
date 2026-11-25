package org.dromara.label.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 类别创建/编辑请求
 */
@Data
public class ClassDTO implements Serializable {

    /** 类别名称 */
    private String labelName = "新标签";

    /** 显示颜色 */
    private String color = "#4f8cff";

    /**
     * 标注工具（bbox/polygon/brush/box3d/cuboid/polyline/keypoint/segmentation），
     * 空或 null 表示不限，标注页不强制切换工具。
     */
    private String toolType = "";

    /** 3D 框初始尺寸-长（米，几何 size.x）；null 表示不预设，下框时沿用拖拽尺寸 */
    private Double defaultSizeL;
    /** 3D 框初始尺寸-宽（米，几何 size.y）；null 表示不预设 */
    private Double defaultSizeW;
    /** 3D 框初始尺寸-高（米，几何 size.z）；null 表示不预设 */
    private Double defaultSizeH;

    /**
     * 刚体标识（仅 tool_type=cuboid 时生效）：true=同一轨迹(id)的立体框尺寸全程保持一致，
     * 任一关键帧改尺寸会同步到该轨迹所有帧（关键帧+插值帧）；null/false=非刚体，尺寸各帧独立、
     * 需手动「同步尺寸到」。null 表示沿用库中原值（编辑其它字段时不清空）。
     */
    private Boolean isRigid;

    /**
     * 立体框内最少点云数（仅 tool_type=cuboid 时生效）：拉框后框内点云数必须 ≥ 此值，
     * 否则该框自动取消并提示。null 或 0 表示不限。
     */
    private Integer minInsidePoints;

    /**
     * 关键点模板（仅 tool_type=keypoint 时使用）：
     * { "points": ["左眼", "右眼", ...], "edges": [[0,1],[1,2], ...] }
     * points 为语义点名称列表（顺序即打点与 COCO 导出顺序），edges 为点索引对（骨架连线）。
     */
    private Object keypointTemplate;

    /** 排序值（缺省 0，前端未传或传 null 时兜底为 0） */
    private Integer sortOrder = 0;
}
