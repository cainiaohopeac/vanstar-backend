package org.dromara.label.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 任务成员角色调整请求：把「已是任务成员」的用户设置为任务管理员或恢复为普通成员。
 */
@Data
public class TaskMemberRoleDTO implements Serializable {

    /** 要调整角色的任务成员用户ID */
    @NotNull(message = "请选择要调整的任务成员")
    private Long userId;

    /** 目标任务角色：member=普通成员，admin=任务管理员 */
    @NotBlank(message = "请选择任务角色")
    private String role;
}
