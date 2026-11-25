package org.dromara.label.vo;

import java.io.Serializable;
import lombok.Data;

/**
 * 文本实体标注的返回结构。
 *
 * <p>除落库字段外附带 {@code className} / {@code color}，
 * 让前端渲染高亮时不必再拿 classId 二次查询标签。
 */
@Data
public class TextEntityVO implements Serializable {

  /** 实体ID */
  private Long id;
  /** 实体编号（前端生成） */
  private String entityKey;
  /** 标签ID */
  private Long classId;
  /** 标签名称 */
  private String className;
  /** 标签颜色 */
  private String color;
  /** 起始字符偏移（含） */
  private Integer startOffset;
  /** 结束字符偏移（不含） */
  private Integer endOffset;
  /** 实体文本 */
  private String entityText;
  /** 所属句序号 */
  private Integer sentenceIndex;
}
