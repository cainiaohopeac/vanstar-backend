package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 类别集 VO
 */
@Data
public class ClassSetVO implements Serializable {

    private Long id;
    private Long projectId;
    /** 类别集名称 */
    private String name;
    /** 版本号 */
    private String version;
    /** 状态（draft/active/...） */
    private String status;
}
