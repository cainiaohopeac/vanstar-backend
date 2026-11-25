package org.dromara.label.ocr.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 识别选项（前端覆盖全局配置）。
 */
@Data
public class OcrOptionsDTO implements Serializable {

    /** 识别语言，如 ch / en */
    private String language;

    /** 是否启用方向分类(角度校正) */
    private Boolean useAngleCls;

    /** 多边形：是否将框外区域涂白后再识别 */
    private Boolean maskOutsidePolygon;
}
