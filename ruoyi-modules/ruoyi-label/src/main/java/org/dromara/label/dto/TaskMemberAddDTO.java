package org.dromara.label.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 任务成员配置请求：把团队中的某个用户设置为任务成员。
 */
@Data
public class TaskMemberAddDTO implements Serializable {

    /** 要设置为任务成员的团队用户ID */
    @NotNull(message = "请选择要设置为任务成员的用户")
    private Long userId;

    /**
     * 成员归属的工作流节点标识（项目工作流节点 nodeKey）。
     * 留空时默认归入「标注」节点；成员只有在任务流转到自己所属节点时才能领取标注/审核。
     */
    private String nodeKey;
}
