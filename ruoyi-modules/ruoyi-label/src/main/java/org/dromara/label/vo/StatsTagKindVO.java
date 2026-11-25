package org.dromara.label.vo;

import java.io.Serializable;
import lombok.Data;

/**
 * 成员标签工作量按几何类别的聚合行（成员 × 类别 × 数量）。
 */
@Data
public class StatsTagKindVO implements Serializable {

    private Long memberId;

    private String memberName;

    /** 类别：point=点 / line=线 / box2d=2D框 / seg2d=2D分割框 / box3d=3D框 / seg3d=3D分割。 */
    private String kind;

    /** 该类别标签数量（只统计有效实框）。 */
    private Long cnt;
}
