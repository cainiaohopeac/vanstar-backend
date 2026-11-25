package org.dromara.label.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 资产 VO
 */
@Data
public class AssetVO implements Serializable {

    private Long id;
    private Long projectId;
    private Long batchId;
    /** 所属上传数据ID（批次下的上传单元，历史数据可能为空） */
    private Long uploadId;
    /** 所属数据条目ID（数据集合包） */
    private Long entryId;
    /** 媒体类型（image/point_cloud/...） */
    private String mediaType;
    /** 文件名 */
    private String fileName;
    /** 文件URL */
    private String fileUrl;
    /** 缩略图URL */
    private String thumbnailUrl;
    /** 文件大小（含单位的展示串） */
    private String fileSize;
    /** 解析状态 */
    private String parseStatus;
    /** 标注状态 */
    private String status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    /** 预览URL（详情/预览接口补充） */
    private String previewUrl;
}
