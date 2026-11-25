package org.dromara.label.vo;

import java.io.Serializable;
import lombok.Data;

/**
 * 成员标签工作量按「几何类别 × 标签类」的聚合行（成员 × 类别 × 标签类 × 数量），
 * 供标签工作量层级表头表格使用：一级=标签数，二级=类别，三级=具体标签名。
 */
@Data
public class StatsTagClassVO implements Serializable {

    private Long memberId;

    private String memberName;

    /** 几何类别：point=点 / line=线 / box2d=2D框 / seg2d=2D分割框 / box3d=3D框 / seg3d=3D分割。 */
    private String kind;

    /** 标签类 ID（label_annotation_object.class_id）。 */
    private Long classId;

    /** 标签类名称（label_class.label_name），class_id 缺失时回落为「未指定标签」。 */
    private String className;

    /** 该成员该类别该标签的数量（只统计有效实框）。 */
    private Long cnt;
}
