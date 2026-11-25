package org.dromara.label.ocr.support;

import org.dromara.label.ocr.vo.OcrResultVO;

import java.awt.image.BufferedImage;

/**
 * OCR 引擎抽象：输入裁剪后的图片 + 语言/方向分类选项，输出识别结果。
 */
public interface OcrEngine {

    OcrResultVO recognize(BufferedImage image, String language, boolean useAngleCls);
}
