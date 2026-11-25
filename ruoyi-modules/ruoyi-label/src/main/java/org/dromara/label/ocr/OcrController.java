package org.dromara.label.ocr;

import org.dromara.label.common.Result;
import org.dromara.label.ocr.dto.OcrRecognizeReq;
import org.dromara.label.ocr.vo.OcrResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * OCR 文本识别接口。
 *
 * <p>路由：POST /ocr/recognize（与前端 /prod-api/ocr/recognize 对齐，无 /label 前缀）。</p>
 */
@RestController
public class OcrController {

    @Autowired
    private OcrService ocrService;

    @PostMapping("/ocr/recognize")
    public Result<OcrResultVO> recognize(@RequestBody OcrRecognizeReq req) {
        return Result.success(ocrService.recognize(req));
    }
}
