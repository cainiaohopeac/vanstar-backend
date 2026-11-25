package org.dromara.label.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.label.common.NuscenesMetaSupport;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * YOLO 标注格式解析器（{@code labels/*.txt} + 类别定义文件）。
 *
 * <p>YOLO 每张图一个同名 txt，每行 {@code class_id cx cy w h [confidence]}，
 * 坐标是相对原图宽高的 <b>0~1 归一化值</b>，而平台落库用的是绝对像素。</p>
 *
 * <p>因此这里输出的 geometry 仍是归一化值并标记 {@code normalized=true}，
 * 由 {@code PreAnnotationServiceImpl} 在匹配到资产后乘以该资产的原图宽高还原。
 * 解析阶段拿不到宽高——它属于资产属性，只有匹配完成才知道是哪张图。</p>
 *
 * <p><b>类别定义文件</b>支持三种，按此优先级：</p>
 * <ol>
 *   <li>{@code class.json} - 数组，每项 {@code {baseLabel, label, color}}，
 *       {@code label} 是类别 id、{@code baseLabel} 是名称。带颜色，导入时可原样补建标签。</li>
 *   <li>{@code classes.txt} - 每行一个类名，行号即 id（YOLOv5 及多数工具链）</li>
 *   <li>{@code data.yaml} - Ultralytics 的 {@code names} 段</li>
 * </ol>
 *
 * <p>同时兼容 YOLO-seg：行内 {@code class_id} 后跟 6 个以上偶数个坐标时视为归一化多边形。</p>
 */
@Component
public class YoloPreAnnotationParser implements PreAnnotationParser {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final int BUFFER_SIZE = 8192;

  /** 类别定义：带颜色的标签设置导出（本平台与常见标注工具采用） */
  private static final String CLASS_JSON = "class.json";
  /** classes.txt（YOLOv5 及多数工具链采用），每行一个类名、行号即 id */
  private static final String CLASSES_TXT = "classes.txt";
  /** data.yaml（Ultralytics 采用，names 段定义类别） */
  private static final String DATA_YAML = "data.yaml";

  /** data.yaml 中 {@code 0: person} 形式的行 */
  private static final Pattern YAML_INDEXED_NAME = Pattern.compile("^\\s*(\\d+)\\s*:\\s*['\"]?([^'\"#]+?)['\"]?\\s*$");
  /** data.yaml 中 {@code names: ['a', 'b']} 或 {@code names: ["a", "b"]} 形式 */
  private static final Pattern YAML_INLINE_NAMES = Pattern.compile("names\\s*:\\s*\\[(.+)]");

  @Override
  public String source() {
    return "yolo";
  }

  @Override
  public String label() {
    return "YOLO（labels/*.txt + class.json）";
  }

  @Override
  public PreAnnotationParseResult parse(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new ServiceException("请选择 YOLO 标签压缩包（含各图同名 txt 与 " + CLASS_JSON + "）");
    }
    String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
    if (!name.endsWith(".zip")) {
      throw new ServiceException("YOLO 格式请上传 zip 压缩包（内含各图同名 txt 与类别定义文件）");
    }

    Map<String, byte[]> entries = readZip(file);
    Map<Integer, String> names = new LinkedHashMap<>();
    Map<Integer, String> colors = new LinkedHashMap<>();
    readClassDefinitions(entries, names, colors);
    // 类别定义文件是<b>可选</b>的：没有它照样导入框，只是拿不到类别名。
    // 此时 sourceClassName 留空，也就不会触发自动补建（没有名字无从补建），
    // 全部走现有匹配逻辑——匹配不上的框在结果里显示「未匹配」，由标注员手工指定标签。
    // 强行报错会让「只有 txt 标签」的数据包完全无法导入，得不偿失。

    Map<String, List<Map<String, Object>>> boxesByFileKey = new LinkedHashMap<>();
    for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
      String entryName = entry.getKey().toLowerCase(Locale.ROOT);
      if (!entryName.endsWith(".txt") || CLASSES_TXT.equals(entryName)) {
        continue;
      }
      // txt 与图片同名（image1.txt ↔ image1.jpg）。
      // 注意：dataFileKey 的扩展名表只有图片与点云（.jpg/.png/.bin/.pcd…），不含 .txt，
      // 直接传 "image1.txt" 会原样保留扩展名，与资产侧的 "image1" 永远对不上。
      // 所以先剥掉 .txt 再取键，否则会出现「名字明明一致却匹配 0 个」。
      String fileKey = NuscenesMetaSupport.dataFileKey(stripExtension(entry.getKey()));
      if (fileKey.isEmpty()) {
        continue;
      }
      List<Map<String, Object>> boxes = new ArrayList<>();
      int seq = 0;
      for (String line : lines(new String(entry.getValue(), StandardCharsets.UTF_8))) {
        Map<String, Object> box = toBox(line, names, seq);
        if (box != null) {
          boxes.add(box);
          seq++;
        }
      }
      if (!boxes.isEmpty()) {
        boxesByFileKey.put(fileKey, boxes);
      }
    }
    if (boxesByFileKey.isEmpty()) {
      throw new ServiceException("未从 YOLO 压缩包中解析出任何标注，请检查 txt 是否位于压缩包内且格式正确");
    }

    PreAnnotationParseResult result = PreAnnotationParseResult.of(boxesByFileKey);
    // 带出类别定义：导入时项目里没有的标签可据此原样补建（含颜色）
    for (Map.Entry<Integer, String> definition : names.entrySet()) {
      result.addClass(definition.getKey(), definition.getValue(), colors.get(definition.getKey()));
    }
    return result;
  }

  // ---------------- 解析单行 ----------------

  /**
   * 单行 → 框。返回 null 表示该行无法解析（空行 / 列数不足 / 数值非法）。
   *
   * <p>{@code class_id cx cy w h [conf]} → 归一化 bbox；
   * {@code class_id} 后跟 ≥6 个偶数个坐标 → 归一化多边形（YOLO-seg）。</p>
   */
  private Map<String, Object> toBox(String line, Map<Integer, String> names, int seq) {
    String[] parts = line.trim().split("\\s+");
    if (parts.length < 5) {
      return null;
    }
    Integer classId = toInt(parts[0]);
    if (classId == null) {
      return null;
    }
    String className = names.get(classId);
    List<Double> coords = new ArrayList<>();
    for (int i = 1; i < parts.length; i++) {
      Double value = toDouble(parts[i]);
      if (value == null) {
        return null;
      }
      coords.add(value);
    }

    Map<String, Object> geometry = new LinkedHashMap<>();
    Map<String, Object> attr = new LinkedHashMap<>();
    attr.put("source", source());
    attr.put("yoloClassId", classId);

    int count = coords.size();
    if (count >= 6 && count % 2 == 0) {
      // YOLO-seg：归一化多边形顶点
      geometry.put("kind", "polygon");
      List<Map<String, Object>> points = new ArrayList<>();
      for (int i = 0; i + 1 < count; i += 2) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("x", round6(coords.get(i)));
        point.put("y", round6(coords.get(i + 1)));
        points.add(point);
      }
      geometry.put("points", points);
      geometry.put("normalized", true);
      return build("yolo-" + seq, "polygon", className, geometry, attr);
    }
    // 标准 YOLO：cx cy w h（+ 可选置信度）
    double cx = coords.get(0);
    double cy = coords.get(1);
    double w = coords.get(2);
    double h = coords.get(3);
    geometry.put("kind", "bbox");
    geometry.put("x", round6(cx - w / 2D));
    geometry.put("y", round6(cy - h / 2D));
    geometry.put("width", round6(w));
    geometry.put("height", round6(h));
    geometry.put("normalized", true);
    if (coords.size() >= 5) {
      attr.put("confidence", coords.get(4));
    }
    return build("yolo-" + seq, "bbox", className, geometry, attr);
  }

  private Map<String, Object> build(String objectKey, String objectType, String className,
                                    Map<String, Object> geometry, Map<String, Object> attr) {
    Map<String, Object> box = new LinkedHashMap<>();
    box.put("objectKey", objectKey);
    box.put("objectType", objectType);
    box.put("sourceClassName", className);
    box.put("geometry", geometry);
    box.put("attr", attr);
    box.put("normalized", Boolean.TRUE);
    return box;
  }

  // ---------------- 类别定义 ----------------

  /**
   * 读类别定义，结果写入 names（id → 名称）与 colors（id → 颜色）。
   * 三种文件按 {@code class.json → classes.txt → data.yaml} 优先级，命中即止。
   */
  private void readClassDefinitions(Map<String, byte[]> entries,
                                    Map<Integer, String> names, Map<Integer, String> colors) {
    byte[] classJson = entries.get(CLASS_JSON);
    if (classJson != null && readClassJson(classJson, names, colors)) {
      return;
    }
    byte[] classes = entries.get(CLASSES_TXT);
    if (classes != null) {
      int id = 0;
      for (String line : lines(new String(classes, StandardCharsets.UTF_8))) {
        names.put(id++, line);
      }
    }
    if (!names.isEmpty()) {
      return;
    }
    byte[] yaml = entries.get(DATA_YAML);
    if (yaml != null) {
      int id = 0;
      for (String name : namesFromYaml(new String(yaml, StandardCharsets.UTF_8))) {
        names.put(id++, name);
      }
    }
  }

  /**
   * 解析 {@code class.json}：数组，每项形如 {@code {"baseLabel":"水稻田","label":0,"color":"#5C5CE5"}}。
   * 其中 {@code label} 是<b>类别 id</b>（数字），{@code baseLabel} 才是名称——两者别搞反。
   *
   * @return 是否成功解析出至少一个类别
   */
  private boolean readClassJson(byte[] bytes, Map<Integer, String> names, Map<Integer, String> colors) {
    Object root;
    try {
      root = MAPPER.readValue(bytes, Object.class);
    } catch (IOException e) {
      return false;
    }
    if (!(root instanceof List<?> list)) {
      return false;
    }
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> raw)) {
        continue;
      }
      Map<String, Object> map = cast(raw);
      // 名称优先取 baseLabel（本平台导出口径），兼容 name / labelName 等
      Object name = firstPresent(map, "baseLabel", "name", "labelName", "className");
      if (name == null) {
        continue;
      }
      // id 取 label（本平台导出口径）或 id；都没有时按出现顺序编号
      Object rawId = firstPresent(map, "label", "id", "classId");
      int id = rawId == null ? names.size() : toIntOr(String.valueOf(rawId), names.size());
      names.put(id, String.valueOf(name).trim());
      Object color = map.get("color");
      if (color != null) {
        colors.put(id, String.valueOf(color).trim());
      }
    }
    return !names.isEmpty();
  }

  /**
   * 从 data.yaml 提取 names。只处理两种最常见写法，避免为了读一个数组引入 YAML 解析依赖：
   * <pre>
   * names: ['person', 'car']            # 行内数组
   * names:
   *   0: person
   *   1: car                            # 索引映射
   * </pre>
   */
  private List<String> namesFromYaml(String yaml) {
    Matcher inline = YAML_INLINE_NAMES.matcher(yaml);
    if (inline.find()) {
      List<String> names = new ArrayList<>();
      for (String item : inline.group(1).split(",")) {
        String name = item.trim().replace("'", "").replace("\"", "");
        if (!name.isEmpty()) {
          names.add(name);
        }
      }
      if (!names.isEmpty()) {
        return names;
      }
    }
    // 索引映射：按行号排序后取名字
    Map<Integer, String> byIndex = new LinkedHashMap<>();
    boolean inNames = false;
    for (String raw : yaml.split("\\R")) {
      String line = raw.trim();
      if (line.startsWith("names:")) {
        inNames = true;
        continue;
      }
      if (!inNames) {
        continue;
      }
      // 遇到下一个顶层键（无缩进且形如 key:）即 names 段结束
      if (!raw.startsWith(" ") && !raw.startsWith("\t") && line.contains(":")) {
        break;
      }
      Matcher matcher = YAML_INDEXED_NAME.matcher(line);
      if (matcher.matches()) {
        byIndex.put(Integer.valueOf(matcher.group(1)), matcher.group(2));
      }
    }
    if (byIndex.isEmpty()) {
      return List.of();
    }
    int max = byIndex.keySet().stream().max(Integer::compareTo).orElse(-1);
    List<String> names = new ArrayList<>();
    for (int i = 0; i <= max; i++) {
      names.add(byIndex.getOrDefault(i, ""));
    }
    return names;
  }

  // ---------------- 读文件 ----------------

  private Map<String, byte[]> readZip(MultipartFile file) {
    Map<String, byte[]> entries = new LinkedHashMap<>();
    try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(file.getInputStream()))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (entry.isDirectory()) {
          continue;
        }
        String base = baseName(entry.getName());
        // YOLO 数据包通常把原图一起打包（可达数百 MB），只取标签与类别定义，
        // 图片整包读入内存会直接 OOM
        if (!isNeeded(base)) {
          continue;
        }
        // 同名条目保留先出现的（labels/ 与根目录重名时以先读到为准）
        entries.putIfAbsent(base, readAll(zis));
      }
    } catch (IOException e) {
      throw new ServiceException("读取 YOLO 压缩包失败：" + e.getMessage());
    }
    if (entries.isEmpty()) {
      throw new ServiceException("YOLO 压缩包内没有找到标签 txt 或类别定义文件");
    }
    return entries;
  }

  /** 是否是需要读取的条目：标签 txt 与类别定义文件 */
  private boolean isNeeded(String baseName) {
    String lower = baseName.toLowerCase(Locale.ROOT);
    return lower.endsWith(".txt") || lower.endsWith(".json")
        || lower.endsWith(".yaml") || lower.endsWith(".yml");
  }

  private String baseName(String path) {
    String normalized = path.replace('\\', '/');
    return normalized.substring(normalized.lastIndexOf('/') + 1);
  }

  /** 去掉最后一个扩展名：image1.txt → image1（让 txt 与同名图片落到同一个匹配键） */
  private String stripExtension(String name) {
    int dot = name.lastIndexOf('.');
    return dot > 0 ? name.substring(0, dot) : name;
  }

  private List<String> lines(String content) {
    List<String> result = new ArrayList<>();
    for (String raw : content.split("\\R")) {
      String line = raw.trim();
      if (!line.isEmpty() && !line.startsWith("#")) {
        result.add(line);
      }
    }
    return result;
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

  // ---------------- 工具 ----------------

  @SuppressWarnings("unchecked")
  private Map<String, Object> cast(Map<?, ?> map) {
    return (Map<String, Object>) map;
  }

  /** 取第一个非空字段值 */
  private Object firstPresent(Map<String, Object> map, String... keys) {
    for (String key : keys) {
      Object value = map.get(key);
      if (value != null && !String.valueOf(value).isBlank()) {
        return value;
      }
    }
    return null;
  }

  private int toIntOr(String value, int fallback) {
    Integer parsed = toInt(value);
    return parsed == null ? fallback : parsed;
  }

  private Integer toInt(String value) {
    try {
      return Integer.valueOf(value.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private Double toDouble(String value) {
    try {
      return Double.valueOf(value.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private double round6(double value) {
    return Math.round(value * 1_000_000D) / 1_000_000D;
  }
}
