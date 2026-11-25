package org.dromara.label.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 质检预检项 VO
 */
@Data
public class PrecheckVO implements Serializable {

    private Long id;
    private Long sequenceId;
    private Long frameId;
    private String objectKey;
    private String checkType;
    private String message;
    private Integer resolvedFlag;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
