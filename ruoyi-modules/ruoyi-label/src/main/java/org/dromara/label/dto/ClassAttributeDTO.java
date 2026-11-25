package org.dromara.label.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 类别属性配置请求
 */
@Data
public class ClassAttributeDTO implements Serializable {

    /** 属性名 */
    private String attrName = "属性";

    /** 属性类型（select/input/...） */
    private String attrType = "select";

    /** 可选项（select 类型时使用） */
    private List<Object> attrOptions = List.of();

    /** 是否必填：0否 1是 */
    private Integer required = 0;

    /**
     * 文本最大字符数（仅 attrType='text' 时生效；null/0 表示不限制）
     * 默认建议 200，与前端默认值一致
     */
    private Integer maxLength;
}
