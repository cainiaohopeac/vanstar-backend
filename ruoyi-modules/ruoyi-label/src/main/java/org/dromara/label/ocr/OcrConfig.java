package org.dromara.label.ocr;

import org.dromara.label.ocr.support.HttpOcrEngine;
import org.dromara.label.ocr.support.MockOcrEngine;
import org.dromara.label.ocr.support.OcrEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OCR 引擎装配：按 ocr.provider 选择 mock / paddle(Http)。
 */
@Configuration
public class OcrConfig {

    @Bean
    public OcrEngine ocrEngine(OcrProperties props) {
        if ("mock".equalsIgnoreCase(props.getProvider())) {
            return new MockOcrEngine();
        }
        return new HttpOcrEngine(props);
    }
}
