package org.dromara.label.ocr.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 资产取图引用（仅取识别所需字段）。
 */
@Data
public class AssetImageRef implements Serializable {

    /** MinIO 对象键（可能带前导斜杠） */
    private String fileUrl;

    /** 媒体类型（image/point_cloud/...） */
    private String mediaType;
}
