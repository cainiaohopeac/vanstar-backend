package org.dromara.label.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 导入分片
 */
@Data
public class ImportPart implements Serializable {

    private Long id;
    /** 导入记录ID */
    private Long recordId;
    /** 分片序号（从1开始） */
    private Integer partNumber;
    /** MinIO 分片 ETag */
    private String etag;
    /** 分片大小（字节） */
    private Long partSize;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
