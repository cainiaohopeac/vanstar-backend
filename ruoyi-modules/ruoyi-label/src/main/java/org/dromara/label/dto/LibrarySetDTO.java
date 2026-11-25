package org.dromara.label.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 标签库·标签集创建/编辑请求
 */
@Data
public class LibrarySetDTO implements Serializable {

    /** 标签集ID（更新时必传，创建时不传） */
    private Long id;

    /** 适用场景：image=2d图片 / point_cloud=3d点云 / text=文字（创建必传，更新忽略） */
    private String scene;

    /** 标签集名称 */
    private String name;

    /** 标签集描述 */
    private String description;

    /** 排序（同场景内展示顺序） */
    private Integer sortOrder;
}
