package org.dromara.label.parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 标注格式解析器的输出：标注框 + 源格式自带的类别定义。
 *
 * <p>之所以把「类别定义」单独带出来，而不只给框上的类别名：
 * 导入时若项目里没有对应标签，平台要能按源定义<b>原样补建</b>（名称 + 颜色），
 * 而不是让标注员对着一堆「未匹配」手工重建。COCO 的 categories、YOLO 的 class.json
 * 都是权威的标签设置，丢掉颜色就得重挑一遍。</p>
 */
public class PreAnnotationParseResult {

  /** 文件匹配键（去扩展名、小写）→ 该图的标注框列表 */
  private Map<String, List<Map<String, Object>>> boxesByFileKey = new LinkedHashMap<>();

  /**
   * 源格式自带的类别定义，每项 {@code {id, name, color}}（color 可为空）。
   *
   * <p>用于导入时补建项目标签：名称是匹配依据，颜色仅在源格式提供时才有
   * （YOLO 的 class.json 有，COCO / 点位导出没有，后者由平台按调色板分配）。</p>
   */
  private List<Map<String, Object>> classDefinitions = new ArrayList<>();

  public static PreAnnotationParseResult of(Map<String, List<Map<String, Object>>> boxes) {
    PreAnnotationParseResult result = new PreAnnotationParseResult();
    result.boxesByFileKey = boxes == null ? new LinkedHashMap<>() : boxes;
    return result;
  }

  /** 追加一条类别定义（id 可为空，名称必填） */
  public void addClass(Object id, String name, String color) {
    if (name == null || name.isBlank()) {
      return;
    }
    Map<String, Object> definition = new LinkedHashMap<>();
    definition.put("id", id);
    definition.put("name", name.trim());
    if (color != null && !color.isBlank()) {
      definition.put("color", color.trim());
    }
    classDefinitions.add(definition);
  }

  public Map<String, List<Map<String, Object>>> getBoxesByFileKey() {
    return boxesByFileKey;
  }

  public void setBoxesByFileKey(Map<String, List<Map<String, Object>>> boxesByFileKey) {
    this.boxesByFileKey = boxesByFileKey == null ? new LinkedHashMap<>() : boxesByFileKey;
  }

  public List<Map<String, Object>> getClassDefinitions() {
    return classDefinitions;
  }

  public void setClassDefinitions(List<Map<String, Object>> classDefinitions) {
    this.classDefinitions = classDefinitions == null ? new ArrayList<>() : classDefinitions;
  }
}
