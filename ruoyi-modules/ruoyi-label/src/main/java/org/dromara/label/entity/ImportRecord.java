package org.dromara.label.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 导入记录
 */
@Data
public class ImportRecord implements Serializable {

    /** 状态：待上传 */
    public static final int STATUS_PENDING = 0;
    /** 状态：上传中 */
    public static final int STATUS_UPLOADING = 1;
    /** 状态：已暂停 */
    public static final int STATUS_PAUSED = 2;
    /** 状态：已完成 */
    public static final int STATUS_COMPLETED = 3;
    /** 状态：失败 */
    public static final int STATUS_FAILED = 4;
    /** 状态：处理中（分片合并与资产入库在后台线程执行） */
    public static final int STATUS_PROCESSING = 5;

    private Long id;
    /** 导入用户ID */
    private Long userId;
    /** 所属项目ID */
    private Long projectId;
    /** 目标数据批次ID */
    private Long batchId;
    /** 文件名 */
    private String fileName;
    /** 文件总大小（字节） */
    private Long fileSize;
    /** MinIO 对象名 */
    private String objectName;
    /** MinIO 桶名 */
    private String bucketName;
    /** MinIO 分片上传ID */
    private String uploadId;
    /** 分片大小（字节） */
    private Long chunkSize;
    /** 总分片数 */
    private Integer totalChunks;
    /** 已上传分片数 */
    private Integer uploadedChunks;
    /** 已上传字节数 */
    private Long uploadedBytes;
    /** 状态：0待上传 1上传中 2已暂停 3已完成 4失败 */
    private Integer status;
    /** 错误信息 */
    private String errorMsg;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishTime;
}
