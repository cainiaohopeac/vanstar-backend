package org.dromara.label.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 标签库查询请求：集合列表按 scene 过滤，标签列表按 setId 过滤；删除类接口只带 id。
 */
@Data
public class LibraryQueryDTO implements Serializable {

    /** 场景过滤：image / point_cloud / text；空查全部（仅集合列表使用） */
    private String scene;

    /** 标签集ID（标签列表使用） */
    private Long setId;

    /** 目标记录ID（删除类接口使用） */
    private Long id;
}
