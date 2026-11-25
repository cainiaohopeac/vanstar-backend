package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 任务成员 VO。
 *
 * <p>用于「成员配置」弹窗：展示任务下发团队的全部成员，并标识其是否已被设置为任务成员。</p>
 */
@Data
public class TaskMemberVO implements Serializable {

    /** 任务成员记录ID（label_task_member.id；尚未加入时为空） */
    private Long id;
    /** 用户ID */
    private Long userId;
    /** 登录账号 */
    private String userName;
    /** 用户昵称 */
    private String nickName;
    /** 所属团队名称（多个团队以「、」拼接） */
    private String teamName;
    /** 团队角色（leader/annotator） */
    private String teamRole;
    /** 任务内角色（member=普通成员，admin=任务管理员；尚未加入任务时为空） */
    private String memberRole;
    /** 是否已配置为该任务成员 */
    private Boolean joined;
    /** 归属工作流节点标识（默认 label 标注节点） */
    private String nodeKey;
    /** 归属工作流节点名称 */
    private String nodeName;
}
