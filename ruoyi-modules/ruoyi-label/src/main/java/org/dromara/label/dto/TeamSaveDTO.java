package org.dromara.label.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 保存团队请求
 */
@Data
public class TeamSaveDTO implements Serializable {

    /** 更新时传入团队ID */
    private Long id;
    private String teamName;
    private String leaderName;
    private String status;
}
