package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 当前用户在某任务内的成员配置权限 VO。
 *
 * <p>用于任务详情页决定「成员配置」入口是否展示，以及是否展示「任务管理员」身份标识。</p>
 */
@Data
public class TaskMemberConfigVO implements Serializable {

    /** 当前用户是否有权配置该任务成员（超管、任务创建者、任务下发团队负责人或任务管理员） */
    private Boolean canConfig;
    /** 当前用户是否为该任务的任务管理员 */
    private Boolean admin;
}
