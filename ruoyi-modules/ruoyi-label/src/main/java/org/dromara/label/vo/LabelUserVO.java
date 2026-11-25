package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 平台用户候选 VO
 */
@Data
public class LabelUserVO implements Serializable {

    private Long userId;
    private String userName;
    private String nickName;
}
