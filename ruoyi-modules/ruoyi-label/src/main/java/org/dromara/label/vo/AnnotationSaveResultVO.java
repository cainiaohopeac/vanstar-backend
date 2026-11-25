package org.dromara.label.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 保存标注结果 VO
 */
@Data
public class AnnotationSaveResultVO implements Serializable {

    private Long jobId;
    private Long annotationId;
    private String status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime savedAt;
}
