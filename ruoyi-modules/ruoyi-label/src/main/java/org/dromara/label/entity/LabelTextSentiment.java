package org.dromara.label.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import org.dromara.label.common.TextSentimentLevel;

/**
 * 文本情感分类（label_text_sentiment）。
 *
 * <p>条目级与句级共用一张表，用 {@code levelType} 区分（取值见 {@link TextSentimentLevel}）：
 * 条目级 {@code sentenceIndex} 为 NULL，句级为具体句序号。
 * 合成一张表是为了避免「两种粒度各建一张结构几乎相同的表」。
 */
@Data
public class LabelTextSentiment implements Serializable {

  /** 情感标注ID */
  private Long id;
  /** 所属标注ID */
  private Long annotationId;
  /** 粒度：entry 条目级 / sentence 句级 */
  private String levelType;
  /** 句序号（条目级为 NULL） */
  private Integer sentenceIndex;
  /** 情感标签ID（label_text_class.id，node_type=classification） */
  private Long classId;
  /** 备注 */
  private String remark;
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
