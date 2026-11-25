package org.dromara.label.parser;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.label.common.NuscenesMetaSupport;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 平台「点位 JSON」导出格式的解析器（{@code point_annotations.json}）。
 *
 * <p>这是 {@code ExportServiceImpl} 中 {@code FORMAT_POINT} 导出的<b>逆过程</b>，
 * 编码规则见 {@code ExportServiceImpl#toReferenceLabel}：</p>
 * <table>
 *   <tr><th>drawType</th><th>平台几何</th><th>points 编码</th></tr>
 *   <tr><td>RECTANGLE</td><td>bbox {x,y,width,height}</td><td>[x, y, x+w, y+h] 左上/右下</td></tr>
 *   <tr><td>POLYGON</td><td>polygon</td><td>[x1,y1,x2,y2,...] 扁平</td></tr>
 *   <tr><td>point</td><td>keypoint 或 skeleton</td><td>扁平，<b>两者都写成 point，无法区分</b></td></tr>
 * </table>
 *
 * <p><b>已知信息损失</b>：导出时 keypoint 模板点集与 skeleton 都会写成 {@code point}，
 * 且骨架的 {@code edges}（骨点连线）没有导出。逆解析时单点还原为 keypoint，
 * 多点还原为 skeleton 并置空 edges —— 连线关系需要标注员补，此处无法凭空恢复。</p>
 */
@Component
public class PointPreAnnotationParser implements PreAnnotationParser {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final int BUFFER_SIZE = 8192;

  /** 平台导出时该文件的固定名称 */
  private static final String POINT_FILE = "point_annotations.json";

  @Override
  public String source() {
    return "point";
  }

  @Override
  public String label() {
    return "平台点位导出（point_annotations.json）";
  }

  @Override
  public PreAnnotationParseResult parse(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new ServiceException("请选择平台点位导出文件（" + POINT_FILE + " 或导出 zip）");
    }
    List<Map<String, Object>> frames = readFrames(file);
    Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
    for (Map<String, Object> frame : frames) {
      // info 是资产 file_url（形如 /xxx.jpg，可能带前导斜杠），去目录去扩展名后即匹配键
      String fileKey = NuscenesMetaSupport.dataFileKey(text(frame.get("info")));
      if (fileKey.isEmpty()) {
        continue;
      }
      List<Map<String, Object>> boxes = labelsOf(frame);
      if (!boxes.isEmpty()) {
        result.computeIfAbsent(fileKey, key -> new ArrayList<>()).addAll(boxes);
      }
    }
    if (result.isEmpty()) {
      throw new ServiceException("未从点位文件中解析出任何标注，请确认文件由平台的「点位 JSON」格式导出");
    }
    PreAnnotationParseResult parsed = PreAnnotationParseResult.of(result);
    for (String className : collectClassNames(frames)) {
      parsed.addClass(null, className, null);
    }
    return parsed;
  }

  /**
   * 点位导出没有集中的类别表（每帧各自带 labels），这里从各帧去重收集类别名。
   * 同样没有颜色信息，缺失的标签由平台调色板分配。
   */
  private List<String> collectClassNames(List<Map<String, Object>> frames) {
    Set<String> names = new LinkedHashSet<>();
    for (Map<String, Object> frame : frames) {
      for (Map<String, Object> label : listOf(frame.get("labels"))) {
        String name = text(label.get("label"));
        if (!name.isEmpty()) {
          names.add(name);
        }
      }
    }
    return new ArrayList<>(names);
  }

  // ---------------- 帧 → 框列表 ----------------

  private List<Map<String, Object>> labelsOf(Map<String, Object> frame) {
    List<Map<String, Object>> boxes = new ArrayList<>();
    Map<String, Object> size = frame.get("size") instanceof Map<?, ?> map ? cast(map) : Map.of();
    int width = (int) asDouble(size.get("width"));
    int height = (int) asDouble(size.get("height"));
    int seq = 0;
    for (Map<String, Object> label : listOf(frame.get("labels"))) {
      Map<String, Object> box = toBox(label, width, height, seq);
      if (box != null) {
        boxes.add(box);
        seq++;
      }
    }
    return boxes;
  }

  private Map<String, Object> toBox(Map<String, Object> label, int width, int height, int seq) {
    String drawType = text(label.get("drawType"));
    List<Double> pts = numbersOf(label.get("points"));
    String className = text(label.get("label"));
    Map<String, Object> geometry = new LinkedHashMap<>();
    String objectType;

    if ("RECTANGLE".equalsIgnoreCase(drawType)) {
      if (pts.size() < 4) {
        return null;
      }
      // 导出时写的是左上角与右下角两点，还原成平台 bbox 的 x/y/width/height
      double x1 = pts.get(0);
      double y1 = pts.get(1);
      double x2 = pts.get(2);
      double y2 = pts.get(3);
      geometry.put("kind", "bbox");
      geometry.put("x", round4(Math.min(x1, x2)));
      geometry.put("y", round4(Math.min(y1, y2)));
      geometry.put("width", round4(Math.abs(x2 - x1)));
      geometry.put("height", round4(Math.abs(y2 - y1)));
      objectType = "bbox";
    } else if ("POLYGON".equalsIgnoreCase(drawType)) {
      if (pts.size() < 6) {
        return null;
      }
      geometry.put("kind", "polygon");
      geometry.put("points", toPoints(pts, false));
      objectType = "polygon";
    } else if ("point".equalsIgnoreCase(drawType)) {
      if (pts.size() < 2) {
        return null;
      }
      if (pts.size() == 2) {
        geometry.put("kind", "keypoint");
        geometry.put("x", round4(pts.get(0)));
        geometry.put("y", round4(pts.get(1)));
        objectType = "keypoint";
      } else {
        // 多点：导出时 keypoint 模板与 skeleton 都写成 point，此处只能按 skeleton 还原，
        // edges（骨点连线）在导出时已丢失，留空待标注员补
        geometry.put("kind", "skeleton");
        geometry.put("points", toPoints(pts, true));
        geometry.put("edges", new ArrayList<>());
        objectType = "skeleton";
      }
    } else {
      return null;
    }

    Map<String, Object> box = new LinkedHashMap<>();
    box.put("objectKey", "point-" + text(label.get("id")) + "-" + seq);
    box.put("objectType", objectType);
    box.put("sourceClassName", className);
    box.put("geometry", geometry);
    Map<String, Object> attr = new LinkedHashMap<>();
    attr.put("source", source());
    attr.put("sourceDrawType", drawType);
    attr.put("sourceObjectId", label.get("_id"));
    attr.put("imageWidth", width);
    attr.put("imageHeight", height);
    attr.put("labelGroup", label.get("group"));
    // 多点 point 无法区分 keypoint 模板与 skeleton，标记出来便于后续核对
    if ("skeleton".equals(objectType)) {
      attr.put("edgesMissing", Boolean.TRUE);
    }
    box.put("attr", attr);
    return box;
  }

  // ---------------- 读文件 ----------------

  /** 顶层是<b>数组</b>（与 COCO 的对象结构不同）；支持直接上传 json 或平台导出的 zip。 */
  private List<Map<String, Object>> readFrames(MultipartFile file) {
    String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
    try {
      if (name.endsWith(".zip")) {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(file.getInputStream()))) {
          ZipEntry entry;
          while ((entry = zis.getNextEntry()) != null) {
            if (entry.isDirectory() || !entry.getName().toLowerCase(Locale.ROOT).endsWith(".json")) {
              continue;
            }
            List<Map<String, Object>> frames = readFrames(readAll(zis));
            if (!frames.isEmpty()) {
              return frames;
            }
          }
        }
        throw new ServiceException("压缩包内没有找到点位标注文件（" + POINT_FILE + "）");
      }
      List<Map<String, Object>> frames = readFrames(readAll(file.getInputStream()));
      if (frames.isEmpty()) {
        throw new ServiceException("该文件不是平台点位格式（顶层应为数组，每项含 info / labels）");
      }
      return frames;
    } catch (IOException e) {
      throw new ServiceException("读取点位文件失败：" + e.getMessage());
    }
  }

  private List<Map<String, Object>> readFrames(byte[] bytes) {
    Object root;
    try {
      root = MAPPER.readValue(bytes, Object.class);
    } catch (IOException e) {
      throw new ServiceException("点位文件不是合法 JSON：" + e.getMessage());
    }
    if (root instanceof List<?> list) {
      List<Map<String, Object>> frames = new ArrayList<>();
      for (Object item : list) {
        if (item instanceof Map<?, ?> map) {
          frames.add(cast(map));
        }
      }
      return frames;
    }
    // 少数工具导出时会包一层 {frames: [...]}
    if (root instanceof Map<?, ?> map) {
      return new ArrayList<>(listOf(cast(map).get("frames")));
    }
    return List.of();
  }

  private static byte[] readAll(InputStream in) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    byte[] chunk = new byte[BUFFER_SIZE];
    int read;
    while ((read = in.read(chunk)) > 0) {
      buffer.write(chunk, 0, read);
    }
    return buffer.toByteArray();
  }

  // ---------------- 类型转换 ----------------

  private List<Map<String, Object>> toPoints(List<Double> coords, boolean withVisible) {
    List<Map<String, Object>> points = new ArrayList<>();
    for (int i = 0; i + 1 < coords.size(); i += 2) {
      Map<String, Object> point = new LinkedHashMap<>();
      point.put("x", round4(coords.get(i)));
      point.put("y", round4(coords.get(i + 1)));
      if (withVisible) {
        point.put("visible", Boolean.TRUE);
      }
      points.add(point);
    }
    return points;
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> listOf(Object value) {
    if (!(value instanceof List<?> list)) {
      return List.of();
    }
    List<Map<String, Object>> rows = new ArrayList<>();
    for (Object item : list) {
      if (item instanceof Map<?, ?> map) {
        rows.add((Map<String, Object>) map);
      }
    }
    return rows;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> cast(Map<?, ?> map) {
    return (Map<String, Object>) map;
  }

  private double asDouble(Object value) {
    return value instanceof Number number ? number.doubleValue() : 0D;
  }

  private List<Double> numbersOf(Object value) {
    List<Double> numbers = new ArrayList<>();
    if (!(value instanceof List<?> list)) {
      return numbers;
    }
    for (Object item : list) {
      if (item instanceof Number number) {
        numbers.add(number.doubleValue());
      }
    }
    return numbers;
  }

  private String text(Object value) {
    return value == null ? "" : String.valueOf(value).trim();
  }

  private double round4(double value) {
    return Math.round(value * 10000D) / 10000D;
  }
}
