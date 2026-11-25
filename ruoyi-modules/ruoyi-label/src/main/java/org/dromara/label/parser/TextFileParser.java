package org.dromara.label.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

/**
 * 文本文件的纯文本提取：把 txt / docx / json / csv 统一成一段可标注的正文。
 *
 * <p>之所以先归一成纯文本：标注存储的是<strong>全文级字符偏移</strong>，
 * 若直接存原始格式（docx 的 XML、csv 的行列），偏移就会与渲染文本对不上，
 * 导出 BIO / JSON 也无从下手。规范化的正文同时也是导出的基准文本。
 */
public final class TextFileParser {

  private TextFileParser() {
  }

  /** 支持的文本扩展名（小写）。 */
  private static final List<String> SUPPORTED = List.of(".txt", ".docx", ".json", ".csv");

  /** 单文件大小上限 20MB（需求规格：单文件不超过 20MB）。 */
  public static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

  /** ObjectMapper 构造开销不小且线程安全，静态复用。 */
  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** 判断扩展名是否支持。 */
  public static boolean isSupported(String fileName) {
    if (fileName == null) {
      return false;
    }
    String lower = fileName.toLowerCase(Locale.ROOT);
    return SUPPORTED.stream().anyMatch(lower::endsWith);
  }

  /**
   * 按扩展名提取正文。
   *
   * @throws IllegalArgumentException 扩展名不支持时抛出
   */
  public static String parse(InputStream in, String fileName) throws IOException {
    String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".docx")) {
      return parseDocx(in);
    }
    if (lower.endsWith(".json")) {
      return parseJson(readAll(in));
    }
    if (lower.endsWith(".csv")) {
      return parseCsv(readAll(in));
    }
    if (lower.endsWith(".txt")) {
      return readAll(in);
    }
    throw new IllegalArgumentException("不支持的文本格式：" + fileName + "（支持 txt/docx/json/csv）");
  }

  // ---------------- 各格式解析 ----------------

  /** docx：按段落提取正文，段间用换行分隔（保留段落结构便于分句）。 */
  private static String parseDocx(InputStream in) throws IOException {
    StringBuilder sb = new StringBuilder();
    try (XWPFDocument doc = new XWPFDocument(in)) {
      for (XWPFParagraph para : doc.getParagraphs()) {
        String text = para.getText();
        if (text != null) {
          sb.append(text).append('\n');
        }
      }
    }
    return sb.toString();
  }

  /**
   * json：支持两种形态
   * <ul>
   *   <li>{"text": "..."} 或 {"content": "..."} —— 单篇</li>
   *   <li>[{"text": "..."}, ...] —— 多篇，按顺序以空行拼接成一篇（导入只产出一个条目）</li>
   * </ul>
   */
  private static String parseJson(String raw) {
    String trimmed = raw == null ? "" : raw.trim();
    if (trimmed.isEmpty()) {
      return "";
    }
    try {
      JsonNode root = MAPPER.readTree(trimmed);
      if (root.isArray()) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode node : root) {
          sb.append(extractTextField(node)).append("\n\n");
        }
        return sb.toString().trim();
      }
      return extractTextField(root);
    } catch (IOException e) {
      throw new IllegalArgumentException("JSON 解析失败：" + e.getMessage(), e);
    }
  }

  /** 从 JSON 节点取正文：优先 text，其次 content。 */
  private static String extractTextField(JsonNode node) {
    JsonNode t = node.get("text");
    if (t == null) {
      t = node.get("content");
    }
    return t == null ? "" : t.asText("");
  }

  /**
   * csv：首行作为表头，找 text 列（其次 content），其余列忽略；
   * 每行一篇，以空行拼接。找不到正文列则整份按纯文本处理。
   */
  private static String parseCsv(String raw) {
    if (raw == null || raw.isBlank()) {
      return "";
    }
    List<String> lines = splitLines(raw);
    if (lines.isEmpty()) {
      return "";
    }
    List<String> header = parseCsvLine(lines.get(0));
    int textCol = indexOfColumn(header, "text", "content");
    if (textCol < 0) {
      // 没有正文列：整份当作纯文本，避免直接丢数据
      return raw;
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 1; i < lines.size(); i++) {
      List<String> cells = parseCsvLine(lines.get(i));
      if (textCol < cells.size()) {
        sb.append(cells.get(textCol)).append('\n');
      }
    }
    return sb.toString().trim();
  }

  private static int indexOfColumn(List<String> header, String... candidates) {
    for (String candidate : candidates) {
      for (int i = 0; i < header.size(); i++) {
        if (candidate.equalsIgnoreCase(header.get(i).trim())) {
          return i;
        }
      }
    }
    return -1;
  }

  /** 最小可用的 CSV 行解析：支持双引号包裹与转义的引号。 */
  private static List<String> parseCsvLine(String line) {
    List<String> cells = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean inQuotes = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (inQuotes) {
        if (c == '"') {
          if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
            cur.append('"');
            i++;
          } else {
            inQuotes = false;
          }
        } else {
          cur.append(c);
        }
      } else if (c == '"') {
        inQuotes = true;
      } else if (c == ',') {
        cells.add(cur.toString().trim());
        cur.setLength(0);
      } else {
        cur.append(c);
      }
    }
    cells.add(cur.toString().trim());
    return cells;
  }

  private static List<String> splitLines(String raw) {
    List<String> lines = new ArrayList<>();
    for (String line : raw.split("\r\n|\n|\r")) {
      if (!line.isBlank()) {
        lines.add(line);
      }
    }
    return lines;
  }

  // ---------------- 编码 ----------------

  /**
   * 读取全部文本并做编码嗅探：UTF-8 BOM → UTF-8 → GBK。
   *
   * <p>不做嗅探直接用平台默认编码读中文 txt 会乱码，而乱码会让字符偏移与导出结果全错。
   */
  private static String readAll(InputStream in) throws IOException {
    byte[] bytes = in.readAllBytes();
    if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB
        && (bytes[2] & 0xFF) == 0xBF) {
      return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
    }
    Charset charset = StandardCharsets.UTF_8;
    String decoded = new String(bytes, charset);
    // UTF-8 解码出现替换字符说明不是 UTF-8，回退 GBK（中文场景最常见）
    if (decoded.indexOf('\uFFFD') >= 0) {
      decoded = new String(bytes, Charset.forName("GBK"));
    }
    return decoded;
  }

}
