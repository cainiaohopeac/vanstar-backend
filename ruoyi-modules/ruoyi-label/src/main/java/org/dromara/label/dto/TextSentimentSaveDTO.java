package org.dromara.label.dto;

import java.io.Serializable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.dromara.label.common.TextSentimentLevel;

/**
 * 文本情感分类的保存入参。
 *
 * <p>{@code levelType} 决定 {@code sentenceIndex} 是否必填：
 * 句级必填、条目级必须为空（服务端校验，避免脏数据）。
 */
@Data
public class TextSentimentSaveDTO implements Serializable {

  /** 粒度：entry 条目级 / sentence 句级，取值见 {@link TextSentimentLevel} */
  @NotBlank(message = "levelType 不能为空")
  private String levelType;

  /** 句序号（句级必填；条目级为空） */
  private Integer sentenceIndex;

  /** 情感标签ID（label_text_class.id，须为 node_type=classification 的标签） */
  @NotNull(message = "classId 不能为空")
  private Long classId;

  /** 备注 */
  private String remark;
}
