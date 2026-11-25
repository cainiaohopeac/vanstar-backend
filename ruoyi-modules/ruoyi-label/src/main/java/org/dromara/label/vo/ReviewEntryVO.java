package org.dromara.label.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 条目级审核 VO（审核工作台「待领取/审核中/复审中/已通过/已驳回」列表）。
 *
 * <p>审核状态与标注状态相互独立：审核通过不修改标注状态。</p>
 */
@Data
public class ReviewEntryVO implements Serializable {

    /** 数据条目标识（label_data_entry.id，一个条目 = 一个数据集合包） */
    private Long id;
    /** 承载该条目数据的作业ID（审核 / 查看时打开标注工作台用，无作业时为空） */
    private Long jobId;
    /** 条目编号（系统生成，唯一） */
    private String entryNo;
    /** 条目名称（数据集合包名，默认取上传文件名） */
    private String entryName;
    /** 条目包含的数据条数 */
    private Long assetCount;
    /** 所属任务 */
    private Long taskId;
    private String taskNo;
    private String taskName;
    /** 导入批次 */
    private Long batchId;
    private String batchName;
    /** 数据类型（media_type，包内混合为 mixed） */
    private String mediaType;
    /** 条目文件（兼容列，取条目名称） */
    private String fileName;
    /** 条目封面（包内首条数据） */
    private String fileUrl;
    private String thumbnailUrl;

    /** 标注状态（字典 label_entry_status） */
    private String labelStatus;
    /** 审核状态（字典 label_review_status） */
    private String reviewStatus;
    /** 审核轮次 */
    private Integer reviewRound;
    /** 不通过次数 */
    private Integer rejectCount;

    /** 标注员 */
    private Long assigneeId;
    private String assigneeName;
    /** 当前审核人 */
    private Long reviewerId;
    private String reviewerName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime assignTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reviewTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
