package org.dromara.label.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 数据条目 VO（一个条目 = 一个数据集合包）。
 *
 * <p>一次上传的数据默认整包为一个条目（splitMode=whole）；
 * 也可按设置把该上传的数据平均分割成多个条目（splitMode=count/parts），
 * 条目下挂载若干数据资产（label_asset.entry_id）。</p>
 */
@Data
public class DataEntryVO implements Serializable {

    /** 条目ID */
    private Long id;
    private Long projectId;
    /** 所属批次 */
    private Long batchId;
    private String batchName;
    /** 所属上传数据 */
    private Long uploadId;
    private String uploadNo;
    /** 所属上传文件名 */
    private String uploadName;

    /** 条目编号（系统生成，唯一） */
    private String entryNo;
    /** 条目名称（默认上传文件名，分割后追加序号） */
    private String entryName;
    /** 上传源文件（原始压缩包名，拆分条目时冗余便于直接展示） */
    private String sourceFile;
    /** 上传内序号（从1开始） */
    private Integer seqNo;
    /** 条目内数据条数 */
    private Long assetCount;
    /** 分割方式：whole不分割/count按数量/parts按份数 */
    private String splitMode;

    /** 所属任务ID（批次下发后回填） */
    private Long taskId;
    /** 条目标注状态（字典 label_entry_status） */
    private String labelStatus;
    /** 条目审核状态（字典 label_review_status） */
    private String reviewStatus;
    /** 审核轮次 */
    private Integer reviewRound;

    private Long assigneeId;
    private String assigneeName;
    private Long reviewerId;
    private String reviewerName;
    private Integer rejectCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime assignTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reviewTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
