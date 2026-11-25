package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 标注对象 VO
 */
@Data
public class AnnotationObjectVO implements Serializable {

    private String objectKey;
    private String type;
    private Long classId;
    private String className;
    private String color;
    /** 几何 JSON 文本 */
    private String geometryJson;
    /** 属性 JSON 文本 */
    private String attrJson;
    private String trackToken;
    private Long instanceId;
    private String visibility;
}
