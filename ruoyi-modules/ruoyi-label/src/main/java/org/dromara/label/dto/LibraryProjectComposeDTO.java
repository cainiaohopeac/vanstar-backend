package org.dromara.label.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 项目引入标签库·任选组合请求：从多个标签库集里任选若干标签，组成项目下的一个标签集合。
 * 文本标签保留层级：勾选中带父子的标签时父子关系一并复制（父未勾选时该子标签提升为顶层）。
 */
@Data
public class LibraryProjectComposeDTO implements Serializable {

    /** 目标项目ID */
    private Long projectId;

    /** 项目侧标签集合名称 */
    private String name;

    /** 勾选的标签库标签ID集合（可跨多个标签集） */
    private List<Long> libraryLabelIds;
}
