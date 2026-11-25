package org.dromara.label.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 交付记录 VO
 */
@Data
public class DeliveryRecordVO implements Serializable {

    private Long id;
    private String projectName;
    private Long exportJobId;
    private String datasetVersion;
    private String format;
    private Long assetCount;
    private String deliveredBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deliveredTime;
}
