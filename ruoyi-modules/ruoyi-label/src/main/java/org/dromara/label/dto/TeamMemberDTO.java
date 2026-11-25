package org.dromara.label.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 团队成员保存请求项
 */
@Data
public class TeamMemberDTO implements Serializable {

    private Long userId;
    private String teamRole;
}
