package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 项目成员（队伍成员）VO
 */
@Data
public class ProjectMemberVO implements Serializable {

    /** 团队成员记录ID */
    private Long id;
    private Long teamId;
    /** 队伍名称 */
    private String teamName;
    private Long userId;
    /** 昵称 */
    private String nickName;
    /** 用户名 */
    private String userName;
    /** 队伍内角色 */
    private String teamRole;
}
