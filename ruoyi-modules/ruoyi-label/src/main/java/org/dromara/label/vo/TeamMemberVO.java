package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 团队成员 VO
 */
@Data
public class TeamMemberVO implements Serializable {

    private Long id;
    private Long teamId;
    private Long userId;
    private String nickName;
    private String userName;
    private String teamRole;
}
