package org.dromara.label.ocr.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.dromara.label.ocr.OcrProperties;
import org.dromara.label.ocr.vo.OcrLineVO;
import org.dromara.label.ocr.vo.OcrResultVO;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 基于 HTTP 调用 PaddleOCR 服务的引擎。
 *
 * <p>约定：以 multipart/form-data 上传裁剪图（字段 {@code image}）与表单字段
 * {@code language}/{@code use_angle_cls}，引擎返回 JSON。两种常见响应均兼容：
 * <ul>
 *   <li>数组：[{transcription, points:[[x,y]...], score}, ...]（PaddleOCR 服务常见形态）</li>
 *   <li>对象：{success, transcription, lines:[...], confidence, engine, message}</li>
 * </ul>
 * 若真实服务返回结构不同，调整 {@link #parse(String)} 即可，不影响上层。</p>
 */
public class HttpOcrEngine implements OcrEngine {

    private final String endpoint;
    private final int timeoutMs;
    private final OkHttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public HttpOcrEngine(OcrProperties props) {
        this.endpoint = props.getEndpoint();
        this.timeoutMs = props.getTimeoutMs();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build();
    }

    @Override
    public OcrResultVO recognize(BufferedImage image, String language, boolean useAngleCls) {
        OcrResultVO result = new OcrResultVO();
        result.setSuccess(false);
        if (endpoint == null || endpoint.isBlank()) {
            result.setMessage("未配置 OCR 服务地址(ocr.endpoint)");
            return result;
        }
        try {
            byte[] png = toPng(image);
            RequestBody body = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("language", language == null ? "ch" : language)
                    .addFormDataPart("use_angle_cls", String.valueOf(useAngleCls))
                    .addFormDataPart("image", "crop.png",
                            RequestBody.create(png, MediaType.parse("image/png")))
                    .build();
            Request req = new Request.Builder().url(endpoint).post(body).build();
            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) {
                    result.setMessage("OCR 服务返回异常: HTTP " + resp.code());
                    return result;
                }
                ResponseBody rb = resp.body();
                String json = rb.string();
                return parse(json);
            }
        } catch (Exception e) {
            result.setMessage("OCR 调用失败: " + e.getMessage());
            return result;
        }
    }

    private byte[] toPng(BufferedImage img) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    private OcrResultVO parse(String json) throws IOException {
        OcrResultVO result = new OcrResultVO();
        JsonNode root = mapper.readTree(json);

        if (root.isArray()) {
            List<OcrLineVO> lines = new ArrayList<>();
            StringBuilder sb = new StringBuilder();
            double sum = 0;
            int n = 0;
            for (JsonNode node : root) {
                OcrLineVO line = toLine(node);
                lines.add(line);
                if (line.getTranscription() != null && !line.getTranscription().isEmpty()) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(line.getTranscription());
                }
                if (line.getScore() != null) {
                    sum += line.getScore();
                    n++;
                }
            }
            result.setSuccess(true);
            result.setLines(lines);
            result.setTranscription(sb.toString());
            result.setConfidence(n > 0 ? sum / n : null);
            result.setEngine("paddleocr");
            return result;
        }

        result.setSuccess(nodeBool(root, "success", true));
        JsonNode tr = root.get("transcription");
        if (tr != null && !tr.isNull()) {
            result.setTranscription(tr.asText());
        }
        JsonNode cf = root.get("confidence");
        if (cf != null && !cf.isNull()) {
            result.setConfidence(cf.asDouble());
        }
        JsonNode eng = root.get("engine");
        if (eng != null && !eng.isNull()) {
            result.setEngine(eng.asText());
        }
        JsonNode lang = root.get("language");
        if (lang != null && !lang.isNull()) {
            result.setLanguage(lang.asText());
        }
        JsonNode diff = root.get("difficult");
        if (diff != null && !diff.isNull()) {
            result.setDifficult(diff.asBoolean());
        }
        JsonNode msg = root.get("message");
        if (msg != null && !msg.isNull()) {
            result.setMessage(msg.asText());
        }
        JsonNode lines = root.get("lines");
        if (lines != null && lines.isArray()) {
            result.setLines(parseLines(lines));
        }
        if ((result.getTranscription() == null || result.getTranscription().isEmpty())
                && result.getLines() != null) {
            StringBuilder sb = new StringBuilder();
            for (OcrLineVO l : result.getLines()) {
                if (l.getTranscription() != null && !l.getTranscription().isEmpty()) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(l.getTranscription());
                }
            }
            result.setTranscription(sb.toString());
        }
        if (result.getEngine() == null) {
            result.setEngine("paddleocr");
        }
        return result;
    }

    private List<OcrLineVO> parseLines(JsonNode nodes) {
        List<OcrLineVO> lines = new ArrayList<>();
        for (JsonNode node : nodes) {
            lines.add(toLine(node));
        }
        return lines;
    }

    private OcrLineVO toLine(JsonNode node) {
        OcrLineVO line = new OcrLineVO();
        JsonNode t = node.get("transcription");
        if (t != null && !t.isNull()) {
            line.setTranscription(t.asText());
        }
        JsonNode s = node.get("score");
        if (s != null && !s.isNull()) {
            line.setScore(s.asDouble());
        }
        JsonNode pts = node.get("points");
        if (pts != null && pts.isArray()) {
            line.setPoints(parsePoints(pts));
        }
        return line;
    }

    private List<List<Integer>> parsePoints(JsonNode pts) {
        List<List<Integer>> out = new ArrayList<>();
        for (JsonNode p : pts) {
            if (p.isArray() && p.size() >= 2) {
                List<Integer> pair = new ArrayList<>();
                pair.add(p.get(0).asInt());
                pair.add(p.get(1).asInt());
                out.add(pair);
            }
        }
        return out;
    }

    private boolean nodeBool(JsonNode node, String field, boolean dflt) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? dflt : v.asBoolean();
    }
}
