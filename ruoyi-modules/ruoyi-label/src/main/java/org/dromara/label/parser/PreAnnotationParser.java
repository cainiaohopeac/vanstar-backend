package org.dromara.label.parser;

import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

/**
 * 预标注格式解析器（策略接口）。
 *
 * <p>每种外部标注格式实现一个解析器，负责把上传文件解析成
 * 「文件匹配键 → 该图的标注框列表」；「按文件名匹配资产 → 类别匹配 → 落库」
 * 由 {@code PreAnnotationServiceImpl} 统一完成。这样新增一种格式只需增加一个实现类，
 * 主流程与前端入口都不用改。</p>
 *
 * <p><b>匹配键</b>沿用 {@code NuscenesMetaSupport#dataFileKey} 的规则
 * （去目录、去扩展名、转小写），与平台 {@code label_asset.file_name} 的口径一致。</p>
 *
 * <p><b>单个框（Map）的字段约定</b>：</p>
 * <ul>
 *   <li>{@code objectKey} - 框唯一键（同一资产内不重复，用于前端去重）</li>
 *   <li>{@code objectType} - 几何类型：bbox / polygon / keypoint / skeleton / cuboid</li>
 *   <li>{@code geometry} - geometry Map（落库前序列化成 geometry_json）</li>
 *   <li>{@code sourceClassName} - 源类别名（用于匹配项目标签）</li>
 *   <li>{@code attr} - 属性 Map（落库前序列化成 attr_json）</li>
 *   <li>{@code normalized} - 为 true 时 geometry 是 0~1 归一化坐标，
 *       落库前需乘以资产的原始宽高还原成像素（YOLO 用）</li>
 * </ul>
 */
public interface PreAnnotationParser {

  /** 来源标识，落库到 label_pre_annotation.source。 */
  String source();

  /** 展示名，用于前端下拉框与错误提示。 */
  String label();

  /**
   * 解析上传文件。
   *
   * @param file 上传的标注文件（json / zip / txt 压缩包，视格式而定）
   * @return 解析结果：文件匹配键 → 框列表，外加源格式自带的类别定义
   */
  PreAnnotationParseResult parse(MultipartFile file);
}
