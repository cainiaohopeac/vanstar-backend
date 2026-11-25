package org.dromara.label.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;

/**
 * 标注对象（保存标注时逐对象提交）
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnnotationObjectDTO implements Serializable {

    /** 对象键（前端生成），为空时服务端生成 */
    private String objectKey;
    /** 对象类型（box/point/...） */
    private String type;
    private Long classId;
    /** 类别名称（可选，缺省 classId 时按名称匹配类别） */
    private String className;
    /** 类别颜色（可选，前端装饰用，随对象一并存档） */
    private String color;
    /** 追踪令牌（跨帧实例关联，序列域使用） */
    private String trackToken;
    /**
     * 分组键（多个对象同组时前端生成，组内成员联动高亮 / 批量操作）
     * 为空表示未分组。
     */
    private String groupKey;
    private String visibility;
    /** 几何（对象或 JSON 文本均可） */
    private Object geometry;
    /** 属性（对象或 JSON 文本均可） */
    private Object attributes;
    /** 几何 JSON 文本（兼容仅传文本的旧前端） */
    private String geometryJson;
    /** 属性 JSON 文本（兼容仅传文本的旧前端） */
    private String attrJson;
}
