package org.dromara.label.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 导出任务 VO
 */
@Data
public class ExportJobVO implements Serializable {

    private Long id;
    private Long projectId;
    private String projectName;
    /** 导出批次ID（批次粒度导出；存量任务为空=项目全量） */
    private Long batchId;
    /** 批次名称 */
    private String batchName;
    private String format;
    private String status;
    private Long assetCount;
    private String resultUrl;
    /** 失败原因（status=failed 时返回） */
    private String errorMessage;
    /** 任务参数 JSON（含 batchIds 等，下载预览还原多批次范围用） */
    private String paramJson;
    private String createdBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
