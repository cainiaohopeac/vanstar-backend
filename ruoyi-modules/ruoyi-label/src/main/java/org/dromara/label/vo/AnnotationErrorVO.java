package org.dromara.label.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 序列标注错误项 VO
 */
@Data
public class AnnotationErrorVO implements Serializable {

    private Long id;
    private Long sequenceId;
    private Long frameId;
    private String objectKey;
    private String errorType;
    private String errorLevel;
    private String status;
    private String comment;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
