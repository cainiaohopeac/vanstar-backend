package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 标注工作台类别 VO
 */
@Data
public class WorkClassVO implements Serializable {

    /** 类别下配置的属性（select=下拉 / text=文本）：画布标签属性行、属性赋值表单、OCR 文本属性定位共用 */
    private List<ClassAttributeVO> attributes;

    private Long classId;
    private String className;
    private String color;
    /**
     * 所属标签集ID / 名称（项目下多个标签集合时，标注页/审核页按此分组，
     * 在类别下拉左侧边缘切换集合；单一集合时前端忽略该字段）。
     */
    private Long classSetId;
    private String classSetName;
    /** 标注工具（空=不限），标注页选中类别后自动切换到该工具 */
    private String toolType;
    /** 3D 框初始尺寸-长（米，几何 size.x）；null 表示不预设 */
    private Double defaultSizeL;
    /** 3D 框初始尺寸-宽（米，几何 size.y）；null 表示不预设 */
    private Double defaultSizeW;
    /** 3D 框初始尺寸-高（米，几何 size.z）；null 表示不预设 */
    private Double defaultSizeH;
    /**
     * 刚体标识（仅立体框类别生效）：true = 同一轨迹的所有帧尺寸自动保持一致（改一处同步整条轨迹）；
     * false/null = 非刚体，各帧尺寸独立，需手动同步。标注页据此决定尺寸改动的同步行为。
     */
    private Boolean isRigid;
    /** 立体框内最少点云数（仅 tool_type=cuboid 生效；null/0 表示不限） */
    private Integer minInsidePoints;
    /**
     * 关键点模板（JSON 文本，前端 JSON.parse）：{ "points": [...], "edges": [[...]] }
     * 仅 tool_type=keypoint 时有值；无配置时为 null（画布退化为单点标注）。
     */
    private String keypointTemplate;
}
