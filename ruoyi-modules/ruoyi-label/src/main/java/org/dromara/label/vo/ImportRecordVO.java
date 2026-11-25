package org.dromara.label.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 导入记录 VO
 * <p>对外返回的导入记录视图，字段与导入记录实体保持一致</p>
 */
@Data
public class ImportRecordVO implements Serializable {

    private Long id;
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
    /** 状态：0待上传 1上传中 2已暂停 3已完成 4失败 5处理中 */
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
