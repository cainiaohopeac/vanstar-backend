package org.dromara.label.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 作业审核/验收记录 VO
 */
@Data
public class ReviewRecordVO implements Serializable {

    private Long id;
    private Long jobId;
    private String reviewerName;
    /** review=审核 acceptance=验收 */
    private String reviewType;
    /** approved/accepted/rejected */
    private String result;
    private String problemType;
    private String problemLevel;
    private String comment;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
