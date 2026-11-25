package org.dromara.label.dto;

import java.io.Serializable;
import lombok.Data;

/**
 * nuScenes 元数据归档上下文：上传链路（ingestZip）创建收集器时的定位信息。
 */
@Data
public class NuscenesArchiveRequest implements Serializable {

    /** 项目ID */
    private Long projectId;
    /** 数据批次ID */
    private Long batchId;
    /** 上传数据ID */
    private Long uploadId;
    /** 上传来源压缩包名（审计展示用） */
    private String sourceName;
    /** 操作人ID */
    private Long userId;
}
