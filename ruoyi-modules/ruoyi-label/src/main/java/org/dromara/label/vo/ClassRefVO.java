package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 类别引用 VO（标注画布/序列标注使用）
 */
@Data
public class ClassRefVO implements Serializable {

    private Long classId;
    private String className;
    private String color;
    private String classSetName;
    /** 标注工具（空=不限），标注页选中类别后自动切换到该工具 */
    private String toolType;
}
