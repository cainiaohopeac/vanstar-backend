package org.dromara.label.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 序列 VO
 */
@Data
public class SequenceVO implements Serializable {

    private Long id;
    private Long projectId;
    /** 所属数据批次ID */
    private Long batchId;
    /** 所属批次名称 */
    private String batchName;
    private String sequenceToken;
    /** 序列名称（场景名） */
    private String name;
    private String description;
    private Integer frameCount;
    private Long firstFrameId;
    private Long lastFrameId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
