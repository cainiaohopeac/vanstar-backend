package org.dromara.label.ocr;

import org.dromara.label.ocr.dto.AssetImageRef;
import org.dromara.label.ocr.dto.OcrGeometryDTO;
import org.dromara.label.ocr.dto.OcrRecognizeReq;
import org.dromara.label.ocr.support.ImageCropUtil;
import org.dromara.label.ocr.support.OcrEngine;
import org.dromara.label.ocr.vo.OcrResultVO;
import org.dromara.label.mapper.OcrMapper;
import org.dromara.label.service.MinioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/**
 * OCR 识别业务：取图 → 裁剪 → 调用引擎。
 */
@Service
public class OcrService {

    @Autowired
    private MinioService minioService;

    @Autowired
    private OcrMapper ocrMapper;

    @Autowired
    private OcrProperties props;

    @Autowired
    private OcrEngine ocrEngine;

    public OcrResultVO recognize(OcrRecognizeReq req) {
        OcrResultVO fail = new OcrResultVO();
        fail.setSuccess(false);

        if (!props.isEnabled()) {
            fail.setMessage("OCR 功能未启用");
            return fail;
        }
        if (req == null || req.getAssetId() == null || req.getGeometry() == null) {
            fail.setMessage("缺少 assetId 或 geometry");
            return fail;
        }

        AssetImageRef asset = ocrMapper.selectAssetById(req.getAssetId());
        if (asset == null) {
            fail.setMessage("资产不存在");
            return fail;
        }
        if (asset.getFileUrl() == null || asset.getFileUrl().isBlank()) {
            fail.setMessage("资产缺少文件");
            return fail;
        }

        String objectKey = asset.getFileUrl().startsWith("/")
                ? asset.getFileUrl().substring(1)
                : asset.getFileUrl();

        BufferedImage img;
        try (InputStream in = minioService.openObject(objectKey)) {
            img = ImageIO.read(in);
        } catch (Exception e) {
            fail.setMessage("读取图片失败: " + e.getMessage());
            return fail;
        }
        if (img == null) {
            fail.setMessage("无法解码图片");
            return fail;
        }

        OcrGeometryDTO g = req.getGeometry();
        if ("BBOX".equalsIgnoreCase(g.getKind())) {
            int w = g.getWidth() == null ? 0 : g.getWidth();
            int h = g.getHeight() == null ? 0 : g.getHeight();
            if (w < props.getMinBoxSize() || h < props.getMinBoxSize()) {
                OcrResultVO small = new OcrResultVO();
                small.setSuccess(true);
                small.setTranscription("");
                small.setEngine(engineName());
                small.setMessage("区域过小，未识别");
                return small;
            }
        }

        BufferedImage crop = ImageCropUtil.crop(img, g, props);
        String language = (req.getOptions() != null && req.getOptions().getLanguage() != null)
                ? req.getOptions().getLanguage()
                : props.getLanguage();
        boolean useAngleCls = (req.getOptions() != null && req.getOptions().getUseAngleCls() != null)
                ? req.getOptions().getUseAngleCls()
                : props.isUseAngleCls();

        OcrResultVO result = ocrEngine.recognize(crop, language, useAngleCls);
        if (result.getLanguage() == null) {
            result.setLanguage(language);
        }
        return result;
    }

    private String engineName() {
        return "mock".equalsIgnoreCase(props.getProvider()) ? "mock" : "paddleocr";
    }
}
