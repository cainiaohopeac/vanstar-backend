package org.dromara.label.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 资产批次 VO
 */
@Data
public class AssetBatchVO implements Serializable {

    private Long id;
    private Long projectId;
    /** 批次号（系统生成，唯一） */
    private String batchNo;
    /** 批次名称（用户可编辑） */
    private String batchName;
    /** 备注 */
    private String remark;
    /** 来源类型（manual/upload/zip/...） */
    private String sourceType;
    /** 来源路径 */
    private String sourcePath;
    /** 状态 */
    private String status;
    /** 资产总数 */
    private Long totalCount;
    /** 成功数 */
    private Long successCount;
    /** 失败数 */
    private Long failedCount;
    /** 批次内数据资产数（实时统计） */
    private Long assetCount;
    /** 批次内数据条目数（数据集合包，实时统计） */
    private Long entryCount;
    /** 批次内待分配条目数（任务详情页批次侧栏用，实时统计） */
    private Long pendingCount;
    /** 批次内上传数据数（实时统计） */
    private Long uploadCount;
    /** 批次内多传感器序列数（实时统计） */
    private Long sequenceCount;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
