package org.dromara.label.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 审核退回请求
 */
@Data
public class ReviewRejectDTO implements Serializable {

    private String comment;
}
