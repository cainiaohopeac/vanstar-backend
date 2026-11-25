package org.dromara.label.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 更新作业状态请求
 */
@Data
public class JobStatusDTO implements Serializable {

    private String status;
}
