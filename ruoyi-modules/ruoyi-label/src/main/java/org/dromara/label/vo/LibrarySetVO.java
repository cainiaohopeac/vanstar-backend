package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 标签库·标签集 VO
 */
@Data
public class LibrarySetVO implements Serializable {

    private Long id;

    /** 适用场景：image / point_cloud / text */
    private String scene;

    /** 标签集名称 */
    private String name;

    /** 标签集描述 */
    private String description;

    /** 排序 */
    private Integer sortOrder;

    /** 集内标签数量（列表接口带出，供页面展示「N 个标签」） */
    private Long labelCount;
}
