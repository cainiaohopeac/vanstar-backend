package org.dromara.label.vo;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * 文本标签（含层级子节点）。
 *
 * <p>返回一棵树：前端标签区按树形折叠展示，叶子（或任意层）标签均可直接用于标注。
 * 快捷键 0-9 由 {@code sortOrder} 决定，取启用标签的前 10 个。
 */
@Data
public class TextClassVO implements Serializable {

  /** 标签ID */
  private Long id;
  /** 父标签ID（null 表示顶层） */
  private Long parentId;
  /** 所属标签集ID（null = 未挂集，直接挂项目） */
  private Long classSetId;
  /** 节点类型：entity / relation / classification */
  private String nodeType;
  /** 标签名称 */
  private String labelName;
  /** 标签颜色 */
  private String color;
  /** 排序（同时决定 0-9 快捷键） */
  private Integer sortOrder;
  /** 子标签（无子节点时为空列表） */
  private List<TextClassVO> children;
}
