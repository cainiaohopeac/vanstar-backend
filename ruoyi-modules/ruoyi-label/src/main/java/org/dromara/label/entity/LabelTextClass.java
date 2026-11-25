package org.dromara.label.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import org.dromara.label.common.TextNodeType;

/**
 * 文本标签（label_text_class）。
 *
 * <p>用 {@code parentId} 表达层级（NULL 为顶层），用 {@code nodeType} 区分
 * 实体 / 关系 / 情感三类节点（取值见 {@link TextNodeType}）。
 *
 * <p>{@code sortOrder} 同时决定前端 0-9 快捷键的顺序：取前 10 个启用标签按此序映射。
 */
@Data
public class LabelTextClass implements Serializable {

  /** 标签ID */
  private Long id;
  /** 项目ID */
  private Long projectId;
  /** 所属标签集ID */
  private Long classSetId;
  /** 父标签ID，NULL 表示顶层 */
  private Long parentId;
  /** 节点类型：entity / relation / classification */
  private String nodeType;
  /** 标签名称 */
  private String labelName;
  /** 标签颜色（前端高亮用） */
  private String color;
  /** 排序（决定展示顺序与快捷键） */
  private Integer sortOrder;
  /** 状态（0正常 1停用） */
  private String status;
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
