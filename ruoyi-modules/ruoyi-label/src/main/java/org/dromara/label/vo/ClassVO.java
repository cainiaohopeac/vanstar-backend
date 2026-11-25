package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 类别 VO
 */
@Data
public class ClassVO implements Serializable {

    private Long id;
    private Long classSetId;
    /** 类别名称 */
    private String labelName;
    /** 显示颜色 */
    private String color;
    /** 标注工具（bbox/polygon/brush/box3d/cuboid/polyline/keypoint/segmentation，空=不限） */
    private String toolType;
    /** 3D 框初始尺寸-长（米，几何 size.x）；null 表示不预设 */
    private Double defaultSizeL;
    /** 3D 框初始尺寸-宽（米，几何 size.y）；null 表示不预设 */
    private Double defaultSizeW;
    /** 3D 框初始尺寸-高（米，几何 size.z）；null 表示不预设 */
    private Double defaultSizeH;
    /**
     * 刚体标识（仅立体框类别生效）：true = 同一轨迹的所有帧尺寸自动保持一致（改一处同步整条轨迹）；
     * false/null = 非刚体，各帧尺寸独立，需手动同步。
     */
    private Boolean isRigid;
    /** 立体框内最少点云数（仅 tool_type=cuboid 生效；null/0 表示不限） */
    private Integer minInsidePoints;
    /**
     * 关键点模板（JSON 文本，前端 JSON.parse）：{ "points": [...], "edges": [[...]] }
     * 仅 tool_type=keypoint 时有值；无配置时为 null（画布退化为单点标注）。
     */
    private String keypointTemplate;
    /** 排序值 */
    private Integer sortOrder;
    /** 状态（active/...） */
    private String status;
    /**
     * 属性配置列表（编辑标签时据此回显；无属性时为空数组而非 null）。
     * 保存走 PUT /classes/{id}/attributes（整份覆盖），无需单独增删。
     */
    private List<ClassAttributeVO> attributes;
}
