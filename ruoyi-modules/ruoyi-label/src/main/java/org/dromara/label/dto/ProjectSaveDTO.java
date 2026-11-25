package org.dromara.label.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 项目创建/编辑请求
 */
@Data
public class ProjectSaveDTO implements Serializable {

    /** 项目ID，为空表示创建 */
    private Long id;

    /** 项目名称 */
    @NotBlank(message = "项目名称不能为空")
    private String projectName;

    /** 项目描述 */
    private String description;

    /** 数据类型（image/point_cloud），缺省 image */
    private String dataType;

    /** 负责人ID（与 ownerName 二选一，优先取 ID） */
    private Long ownerId;

    /** 负责人名称（旧前端兼容，仅当 ownerId 为空时解析） */
    private String ownerName;

    /** 主队伍名称（可选） */
    private String teamName;

    /** 参与队伍ID列表（可选，兼容 JSON 数组） */
    private List<Long> teamIds;

    /** 状态（编辑时忽略，创建时缺省 created） */
    private String status;
}
