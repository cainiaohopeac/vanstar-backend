package org.dromara.label.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 类别集创建/编辑请求
 */
@Data
public class ClassSetDTO implements Serializable {

    /** 类别集名称 */
    private String name = "新标签集";

    /** 版本号 */
    private String version = "v1.0";
}
