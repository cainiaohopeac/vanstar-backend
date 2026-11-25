package org.dromara.label.ocr.support;

import org.dromara.label.ocr.vo.OcrLineVO;
import org.dromara.label.ocr.vo.OcrResultVO;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * 模拟引擎：未接入真实 OCR 服务时返回示例文本，便于前端/后端联调走通整条链路。
 *
 * <p>上线接好 PaddleOCR 后，将 ocr.provider 改为 paddle 即可切换。</p>
 */
public class MockOcrEngine implements OcrEngine {

    @Override
    public OcrResultVO recognize(BufferedImage image, String language, boolean useAngleCls) {
        OcrResultVO r = new OcrResultVO();
        r.setSuccess(true);
        r.setEngine("mock");
        r.setLanguage(language);
        r.setConfidence(0.955);

        OcrLineVO l1 = new OcrLineVO();
        l1.setTranscription("示例文本");
        l1.setScore(0.97);

        OcrLineVO l2 = new OcrLineVO();
        l2.setTranscription("（OCR 模拟）");
        l2.setScore(0.94);

        r.setLines(List.of(l1, l2));
        r.setTranscription("示例文本\n（OCR 模拟）");
        return r;
    }
}
