package org.dromara.label.parser;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.label.common.NuscenesMetaSupport;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * COCO 标注格式解析器（{@code images} / {@code categories} / {@code annotations}）。
 *
 * <p>几何还原顺序：<b>优先 segmentation，其次 bbox</b>。segmentation 是贴合目标轮廓的多边形，
 * 比外接矩形精确，因此只要能解析出矢量多边形就用它；只有遇到 RLE 掩码（iscrowd=1，无矢量表示）
 * 或根本没有 segmentation 时，才退回 {@code bbox=[x, y, w, h]}（左上角 + 宽高，绝对像素）。</p>
 */
@Component
public class CocoPreAnnotationParser implements PreAnnotationParser {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** 闭合多边形的最少坐标分量数（3 个顶点 → 6 个数） */
  private static final int MIN_POLYGON_COORDS = 6;

  private static final int BUFFER_SIZE = 8192;

  @Override
  public String source() {
    return "coco";
  }

  @Override
  public String label() {
    return "COCO（instances*.json）";
  }

  @Override
  public PreAnnotationParseResult parse(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new ServiceException("请选择 COCO 标注文件（instances*.json 或包含它的 zip）");
    }
    Map<String, Object> root = readRoot(file);

    // 类别：必须按 id 建索引。COCO 的 category_id 既不保证从 0 起、也不保证连续，
    // 拿数组下标当 id 会整体错位（样本数据集里 categories[0].id=0，而 annotation 引用的是 4）。
    Map<Integer, String> categoryNames = new LinkedHashMap<>();
    for (Map<String, Object> category : listOf(root.get("categories"))) {
      Integer id = asInt(category.get("id"));
      String name = text(category.get("name"));
      if (id != null && !name.isEmpty()) {
        categoryNames.put(id, name);
      }
    }
    Map<Integer, Map<String, Object>> images = new LinkedHashMap<>();
    for (Map<String, Object> image : listOf(root.get("images"))) {
      Integer id = asInt(image.get("id"));
      if (id != null) {
        images.put(id, image);
      }
    }
    if (images.isEmpty()) {
      throw new ServiceException("COCO 文件中没有 images，无法按文件名匹配图片");
    }

    Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
    for (Map<String, Object> ann : listOf(root.get("annotations"))) {
      Map<String, Object> image = images.get(asInt(ann.get("image_id")));
      if (image == null) {
        continue;
      }
      String fileKey = NuscenesMetaSupport.dataFileKey(text(image.get("file_name")));
      if (fileKey.isEmpty()) {
        continue;
      }
      String className = categoryNames.getOrDefault(asInt(ann.get("category_id")), "");
      int width = (int) asDouble(image.get("width"));
      int height = (int) asDouble(image.get("height"));
      List<Map<String, Object>> boxes = result.computeIfAbsent(fileKey, key -> new ArrayList<>());

      List<List<Double>> polygons = polygonsOf(ann.get("segmentation"));
      if (!polygons.isEmpty()) {
        // 一个 annotation 可能带多个多边形（目标被遮挡切分成多块），逐块还原成独立对象
        for (int part = 0; part < polygons.size(); part++) {
          Map<String, Object> geometry = new LinkedHashMap<>();
          geometry.put("kind", "polygon");
          geometry.put("points", toPoints(polygons.get(part)));
          boxes.add(box(ann, "polygon", className, geometry, width, height, part));
        }
        continue;
      }
      // 无 segmentation / RLE 掩码：退回 bbox
      List<Double> bbox = numbersOf(ann.get("bbox"));
      if (bbox.size() < 4) {
        continue;
      }
      Map<String, Object> geometry = new LinkedHashMap<>();
      geometry.put("kind", "bbox");
      geometry.put("x", round4(bbox.get(0)));
      geometry.put("y", round4(bbox.get(1)));
      geometry.put("width", round4(bbox.get(2)));
      geometry.put("height", round4(bbox.get(3)));
      boxes.add(box(ann, "bbox", className, geometry, width, height, 0));
    }
    if (result.isEmpty()) {
      throw new ServiceException("未从 COCO 文件中解析出任何标注，请检查 annotations 的 image_id 与 images 是否对应");
    }
    PreAnnotationParseResult parsed = PreAnnotationParseResult.of(result);
    // COCO 的 categories 没有颜色字段，只带名称；缺失的标签由平台调色板分配颜色
    for (Map.Entry<Integer, String> category : categoryNames.entrySet()) {
      parsed.addClass(category.getKey(), category.getValue(), null);
    }
    return parsed;
  }

  // ---------------- 读文件 ----------------

  /** 读取 COCO 根对象：支持直接上传 json，或上传包含 json 的 zip。 */
  private Map<String, Object> readRoot(MultipartFile file) {
    String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
    try {
      if (name.endsWith(".zip")) {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(file.getInputStream()))) {
          ZipEntry entry;
          while ((entry = zis.getNextEntry()) != null) {
            if (entry.isDirectory() || !entry.getName().toLowerCase(Locale.ROOT).endsWith(".json")) {
              continue;
            }
            Map<String, Object> parsed = readJson(zis);
            if (parsed != null && parsed.containsKey("annotations") && parsed.containsKey("images")) {
              return parsed;
            }
          }
        }
        throw new ServiceException("压缩包内没有找到 COCO 标注文件（需为含 images 与 annotations 的 json）");
      }
      Map<String, Object> parsed = readJson(file.getInputStream());
      if (parsed == null || !parsed.containsKey("annotations")) {
        throw new ServiceException("该文件不是 COCO 格式（缺少 annotations）");
      }
      return parsed;
    } catch (IOException e) {
      throw new ServiceException("读取 COCO 文件失败：" + e.getMessage());
    }
  }

  private Map<String, Object> readJson(InputStream in) throws IOException {
    byte[] bytes = readAll(in);
    return MAPPER.readValue(bytes, new TypeReference<Map<String, Object>>() { });
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

  // ---------------- 类型转换 / 组装 ----------------

  private Map<String, Object> box(Map<String, Object> ann, String objectType, String className,
                                  Map<String, Object> geometry, int width, int height, int part) {
    Map<String, Object> box = new LinkedHashMap<>();
    String objectKey = "coco-" + text(ann.get("id")) + (part > 0 ? "-" + part : "");
    box.put("objectKey", objectKey);
    box.put("objectType", objectType);
    box.put("sourceClassName", className);
    box.put("geometry", geometry);

    Map<String, Object> attr = new LinkedHashMap<>();
    attr.put("source", source());
    attr.put("cocoAnnotationId", ann.get("id"));
    attr.put("cocoPartIndex", part);
    attr.put("imageWidth", width);
    attr.put("imageHeight", height);
    attr.put("iscrowd", ann.get("iscrowd"));
    attr.put("area", ann.get("area"));
    box.put("attr", attr);
    return box;
  }

  /**
   * 取 segmentation 中的矢量多边形。
   *
   * <p>COCO 有两种写法：多边形 {@code [[x1,y1,x2,y2,...], ...]}，
   * 以及 RLE 掩码 {@code {"counts":..., "size":...}}。RLE 没有矢量轮廓，返回空列表由调用方退回 bbox。</p>
   */
  private List<List<Double>> polygonsOf(Object segmentation) {
    List<List<Double>> result = new ArrayList<>();
    if (!(segmentation instanceof List<?> groups)) {
      return result;
    }
    for (Object group : groups) {
      List<Double> coords = numbersOf(group);
      if (coords.size() >= MIN_POLYGON_COORDS) {
        result.add(coords);
      }
    }
    return result;
  }

  /** 扁平坐标数组 [x1,y1,x2,y2,...] → 平台 polygon 的 points 结构。 */
  private List<Map<String, Object>> toPoints(List<Double> coords) {
    // COCO 用「首尾点重复」表示闭合，平台内部约定<b>不存闭合点</b>（SVG polygon 自动闭合）。
    // 留着会多出一个重合的顶点手柄，并且会被标注前端「钥匙孔接缝」检测误判，
    // 把闭合段当成洞抽出去、矩形渲染成三角形。
    int limit = coords.size();
    if (limit >= MIN_POLYGON_COORDS
        && Double.compare(coords.get(0), coords.get(limit - 2)) == 0
        && Double.compare(coords.get(1), coords.get(limit - 1)) == 0) {
      limit -= 2;
    }
    List<Map<String, Object>> points = new ArrayList<>();
    for (int i = 0; i + 1 < limit; i += 2) {
      Map<String, Object> point = new LinkedHashMap<>();
      point.put("x", round4(coords.get(i)));
      point.put("y", round4(coords.get(i + 1)));
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

  private Integer asInt(Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value instanceof String text && !text.isBlank()) {
      try {
        return Integer.valueOf(text.trim());
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  private double asDouble(Object value) {
    return value instanceof Number number ? number.doubleValue() : 0D;
  }

  private String text(Object value) {
    return value == null ? "" : String.valueOf(value).trim();
  }

  private double round4(double value) {
    return Math.round(value * 10000D) / 10000D;
  }
}
