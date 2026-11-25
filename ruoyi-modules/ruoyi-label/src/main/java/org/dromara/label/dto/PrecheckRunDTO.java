package org.dromara.label.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 运行序列质检预检请求
 */
@Data
public class PrecheckRunDTO implements Serializable {

    @NotNull(message = "缺少 frameId")
    private Long frameId;
}
