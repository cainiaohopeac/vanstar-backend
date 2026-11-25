package org.dromara.label.dto;

import lombok.Data;

import org.dromara.label.common.MediaType;

import java.io.Serializable;

/**
 * 资产上传/导入任务请求（JSON 清单）
 */
@Data
public class ProjectAssetUploadDTO implements Serializable {

    /** 项目ID（旧前端缺省 1） */
    private Long projectId = 1L;

    /** 媒体类型（image/point_cloud/...），缺省 image */
    private String mediaType = MediaType.IMAGE;

    /** 文件名 */
    private String fileName;

    /** 文件大小（支持纯数字字节或 "5 MB" 形式） */
    private String fileSize;
}
