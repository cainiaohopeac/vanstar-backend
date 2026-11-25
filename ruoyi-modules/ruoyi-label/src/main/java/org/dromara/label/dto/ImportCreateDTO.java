package org.dromara.label.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 创建导入请求
 */
@Data
public class ImportCreateDTO {

    /** 所属项目ID */
    @NotNull(message = "项目不能为空")
    private Long projectId;

    /** 目标数据批次ID，可选；为空时自动创建批次 */
    private Long batchId;

    /** 文件名 */
    @NotBlank(message = "文件名不能为空")
    private String fileName;

    /** 文件总大小（字节） */
    @NotNull(message = "文件大小不能为空")
    @Positive(message = "文件大小必须大于0")
    private Long fileSize;

    /** 分片大小（字节），可选，默认使用服务端 minio.chunk-size 配置 */
    private Long chunkSize;
}
