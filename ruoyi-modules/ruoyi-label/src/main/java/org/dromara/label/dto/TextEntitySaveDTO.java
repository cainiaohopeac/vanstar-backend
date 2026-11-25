package org.dromara.label.dto;

import java.io.Serializable;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 文本实体标注的保存入参（前端划词后提交的一条实体）。
 *
 * <p>偏移为<strong>全文级</strong>：{@code startOffset} 含、{@code endOffset} 不含，
 * 与 {@code String#substring} 一致。允许实体嵌套（如「北京市」中嵌套「北京」），
 * 因此不做全局互斥校验，只校验区间自身合法（start &lt; end 且不越界、不跨句）。
 */
@Data
public class TextEntitySaveDTO implements Serializable {

  /** 实体编号（前端生成，同一标注内唯一；用于前端定位与后续关系标注引用） */
  @NotNull(message = "entityKey 不能为空")
  private String entityKey;

  /** 实体标签ID（label_text_class.id，须为 node_type=entity 的标签） */
  @NotNull(message = "classId 不能为空")
  private Long classId;

  /** 起始字符偏移（全文级，含） */
  @NotNull(message = "startOffset 不能为空")
  private Integer startOffset;

  /** 结束字符偏移（全文级，不含） */
  @NotNull(message = "endOffset 不能为空")
  private Integer endOffset;

  /** 实体文本快照（服务端会按偏移重算，此处仅作冗余校验） */
  private String entityText;

  /** 所属句序号（当前约束：实体不得跨句，跨句实体服务端直接拒绝） */
  private Integer sentenceIndex;
}
