package org.dromara.label.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 文本标签集（label_text_class_set）。
 *
 * <p>文本标注使用独立的一套标签体系，不与视觉任务的 label_class_set / label_class 混用：
 * 若共用，同一个 class_id 会指向两张含义不同的表，跨类型复用与统计都会变得不可靠。
 */
@Data
public class LabelTextClassSet implements Serializable {

  /** 标签集ID */
  private Long id;
  /** 项目ID */
  private Long projectId;
  /** 标签集名称 */
  private String setName;
  /** 标签集描述 */
  private String description;
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
