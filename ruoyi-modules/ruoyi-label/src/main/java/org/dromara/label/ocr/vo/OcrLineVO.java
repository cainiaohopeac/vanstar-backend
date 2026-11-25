package org.dromara.label.ocr.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 单行识别结果（与 PPOCRLabel 对齐）。
 */
@Data
public class OcrLineVO implements Serializable {

    /** 该行文本 */
    private String transcription;

    /** 置信度 0~1 */
    private Double score;

    /** 该行四点坐标（原图/裁剪图坐标）：[x,y]×4 */
    private List<List<Integer>> points;
}
