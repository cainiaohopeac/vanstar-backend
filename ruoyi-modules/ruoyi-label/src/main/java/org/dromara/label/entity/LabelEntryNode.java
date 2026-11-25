package org.dromara.label.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 数据条目节点状态（label_entry_node）：按工作流节点记录条目的领取/跳过/放弃/完成状态。
 *
 * <p>每个条目在每个工作流节点各一条记录（entry_id + node_key 唯一）。</p>
 */
@Data
public class LabelEntryNode implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 主键 */
  private Long id;
  /** 数据条目ID */
  private Long entryId;
  /** 任务ID */
  private Long taskId;
  /** 工作流节点标识 */
  private String nodeKey;
  /** 工作流节点类型：label / review */
  private String nodeType;
  /** 当前节点领取/处理人ID */
  private Long operatorId;
  /** 节点状态：pending / claimed / skipped / abandoned / done */
  private String status;
  /** 跳过/放弃前状态 */
  private String prevStatus;
  /** 跳过人 */
  private Long skipBy;
  /** 跳过时间 */
  private LocalDateTime skipTime;
  /** 放弃人 */
  private Long abandonBy;
  /** 放弃时间 */
  private LocalDateTime abandonTime;
  /** 领取时间 */
  private LocalDateTime claimTime;
  /** 完成时间 */
  private LocalDateTime doneTime;

  private Long createdBy;
  private Long updatedBy;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
  private Integer deleted;
}
