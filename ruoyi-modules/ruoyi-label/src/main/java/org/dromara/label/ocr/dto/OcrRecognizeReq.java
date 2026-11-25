package org.dromara.label.ocr.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * OCR 识别请求（前端 → 后端）。
 */
@Data
public class OcrRecognizeReq implements Serializable {

    /** 资产ID（后端据此取原图） */
    private Long assetId;

    /** 裁剪几何：bbox 或 polygon 顶点环（原图像素坐标） */
    private OcrGeometryDTO geometry;

    /** 识别选项（缺省时回退到 ocr.* 全局配置） */
    private OcrOptionsDTO options;
}
