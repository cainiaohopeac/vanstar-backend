package org.dromara.label.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 项目引入标签库·整集请求：把标签库的一个标签集整体复制为项目下的一个标签集合。
 */
@Data
public class LibraryImportDTO implements Serializable {

    /** 目标项目ID */
    private Long projectId;

    /** 标签库标签集ID */
    private Long librarySetId;

    /** 项目侧标签集合名称；缺省用库名 */
    private String name;
}
