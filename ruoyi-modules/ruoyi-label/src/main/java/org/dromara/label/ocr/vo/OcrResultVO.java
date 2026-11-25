package org.dromara.label.ocr.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * OCR 识别结果业务对象（response.data）。
 *
 * <p>字段对齐前端约定与 ICDAR/PPOCRLabel 风格：文本用 {@code transcription}（非 text）。</p>
 */
@Data
public class OcrResultVO implements Serializable {

    /** 是否成功 */
    private Boolean success;

    /** 合并后的整框文本（默认取各行拼接，前端可编辑） */
    private String transcription;

    /** 整框置信度（各行均值，可空） */
    private Double confidence;

    /** 识别语言 */
    private String language;

    /** 引擎标识，如 paddleocr-v5 / mock */
    private String engine;

    /** 逐行结果 */
    private List<OcrLineVO> lines;

    /** 是否为难样本 */
    private Boolean difficult;

    /** 失败时的提示 */
    private String message;
}
