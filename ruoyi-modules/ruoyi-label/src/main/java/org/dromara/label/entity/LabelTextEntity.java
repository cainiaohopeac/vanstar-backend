package org.dromara.label.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 文本实体标注（label_text_entity）。
 *
 * <p>位置用<strong>全文级字符偏移</strong>表达：{@code startOffset} 闭区间、
 * {@code endOffset} 开区间，与 Java 的 {@code substring(start, end)} 语义一致。
 * 之所以不复用 label_annotation_object 的 geometry_json：文本没有几何概念，
 * 把偏移塞进 JSON 后既无法建索引，也无法直接用 SQL 做重叠/包含检索与统计。
 *
 * <p>当前业务约束为「实体不跨句」，因此 {@code sentenceIndex} 既用于快速定位，
 * 也用于保存时校验边界未越句。该约束若后续放开，此字段仍可作为归属提示保留。
 */
@Data
public class LabelTextEntity implements Serializable {

  /** 实体ID（新增时为空，由数据库自增；批量写入不回填） */
  private Long id;
  /** 所属标注ID */
  private Long annotationId;
  /** 实体编号（前端生成，同一标注内唯一） */
  private String entityKey;
  /** 标签ID（label_text_class.id，node_type=entity） */
  private Long classId;
  /** 起始字符偏移（含） */
  private Integer startOffset;
  /** 结束字符偏移（不含） */
  private Integer endOffset;
  /** 实体文本快照（列表与导出用，省去回读全文截取） */
  private String entityText;
  /** 所属句序号（实体不跨句） */
  private Integer sentenceIndex;
  /** 创建人 */
  private Long createBy;
  /** 更新人 */
  private Long updateBy;
  /** 创建时间 */
  private LocalDateTime createTime;
  /** 更新时间 */
  private LocalDateTime updateTime;
  /** 删除标记 */
  private Integer deleted;
}
