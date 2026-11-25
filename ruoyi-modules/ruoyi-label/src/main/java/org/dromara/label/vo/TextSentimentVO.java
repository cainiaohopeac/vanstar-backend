package org.dromara.label.vo;

import java.io.Serializable;
import lombok.Data;

/**
 * 文本情感分类的返回结构（条目级与句级共用）。
 */
@Data
public class TextSentimentVO implements Serializable {

  /** 粒度：entry / sentence */
  private String levelType;
  /** 句序号（条目级为 null） */
  private Integer sentenceIndex;
  /** 情感标签ID */
  private Long classId;
  /** 情感标签名称 */
  private String className;
  /** 情感标签颜色 */
  private String color;
  /** 备注 */
  private String remark;
}
