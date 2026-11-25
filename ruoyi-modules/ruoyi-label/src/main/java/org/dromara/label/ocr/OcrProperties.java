package org.dromara.label.ocr;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * OCR 文本识别配置（application-*.yml 的 ocr.* 段）。
 *
 * <p>与前端约定：多边形默认开启「框外涂白」，语言默认 ch，调用超时 10s。</p>
 */
@Component
public class OcrProperties {

    /** 总开关：false 时接口直接返回「未启用」 */
    @Value("${ocr.enabled:false}")
    private boolean enabled;

    /** 引擎提供方：paddle(Http 调用 PaddleOCR) / mock(返回示例文本，便于无引擎时联调) */
    @Value("${ocr.provider:mock}")
    private String provider;

    /** PaddleOCR 服务地址（provider=paddle 时必填） */
    @Value("${ocr.endpoint:}")
    private String endpoint;

    /** 调用超时(ms) */
    @Value("${ocr.timeout-ms:10000}")
    private int timeoutMs;

    /** 小于该尺寸的框(像素)不识别 */
    @Value("${ocr.min-box-size:32}")
    private int minBoxSize;

    /** 裁剪外扩比例(避免切到文字边缘) */
    @Value("${ocr.padding-ratio:0.08}")
    private double paddingRatio;

    /** 默认识别语言 */
    @Value("${ocr.language:ch}")
    private String language;

    /** 是否启用方向分类(角度校正) */
    @Value("${ocr.use-angle-cls:true}")
    private boolean useAngleCls;

    /** 多边形：是否将框外区域涂白后再识别 */
    @Value("${ocr.mask-outside-polygon:true}")
    private boolean maskOutsidePolygon;

    public boolean isEnabled() {
        return enabled;
    }

    public String getProvider() {
        return provider;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public int getMinBoxSize() {
        return minBoxSize;
    }

    public double getPaddingRatio() {
        return paddingRatio;
    }

    public String getLanguage() {
        return language;
    }

    public boolean isUseAngleCls() {
        return useAngleCls;
    }

    public boolean isMaskOutsidePolygon() {
        return maskOutsidePolygon;
    }
}
