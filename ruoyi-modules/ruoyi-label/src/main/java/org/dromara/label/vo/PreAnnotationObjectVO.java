package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 预标注对象 VO（一个 3D 框）。
 */
@Data
public class PreAnnotationObjectVO implements Serializable {

    private String objectKey;
    private String objectType;
    /** 映射到的项目类别ID；未匹配到类别时为空 */
    private Long classId;
    /** 映射到的项目类别名 */
    private String className;
    /** 类别颜色 */
    private String color;
    /** 源类别名（nuScenes category.name，如 vehicle.car） */
    private String sourceClassName;
    /** 几何 JSON 文本（center / size / rotationZ） */
    private String geometryJson;
    /** 属性 JSON 文本 */
    private String attrJson;
    private String visibility;
    /** 实例Token（跨帧关联） */
    private String trackToken;
}
