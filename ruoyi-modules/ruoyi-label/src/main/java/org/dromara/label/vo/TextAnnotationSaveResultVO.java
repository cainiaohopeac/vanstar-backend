package org.dromara.label.vo;

import java.io.Serializable;
import lombok.Data;

/**
 * 文本标注保存结果。
 */
@Data
public class TextAnnotationSaveResultVO implements Serializable {

  /** 标注存档ID */
  private Long annotationId;
  /** 标注状态（draft / submitted） */
  private String status;
  /** 作业状态（提交后可能推进到 pending_review） */
  private String jobStatus;
  /** 本次落库的实体数 */
  private Integer entityCount;
  /** 本次落库的情感标注数 */
  private Integer sentimentCount;
}
