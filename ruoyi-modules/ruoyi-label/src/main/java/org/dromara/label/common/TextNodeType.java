package org.dromara.label.common;

/**
 * 文本标签的节点类型（label_text_class.node_type）。
 *
 * <p>文本标注一套标签里混装三类节点，用这个字段区分：
 * 实体标签用于划词标注，关系标签用于实体间连线，情感标签用于句级/条目级分类。
 * 前端按类型分区展示，避免把「正面/负面」之类的情感标签混进实体标签列表。
 */
public final class TextNodeType {

  private TextNodeType() {
  }

  /** 实体标签（划词标注） */
  public static final String ENTITY = "entity";
  /** 关系标签（实体间关联） */
  public static final String RELATION = "relation";
  /** 情感/分类标签（句级与条目级共用） */
  public static final String CLASSIFICATION = "classification";
}
