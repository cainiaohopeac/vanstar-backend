package org.dromara.label.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 项目工作流节点（label_workflow_node）：项目隔离的标注流转节点配置。
 *
 * <p>固定节点 raw_data(start) / label(label) / qualified(end) 由系统初始化，
 * 不可删除、不可改 nodeKey；动态节点 review 可增删、改名、排序，项目默认一个 review_1。</p>
 */
@Data
public class LabelWorkflowNode implements Serializable {

  /** 节点ID */
  private Long id;
  /** 所属项目ID（项目隔离） */
  private Long projectId;
  /** 节点标识（项目内唯一：raw_data/label/review_N/qualified） */
  private String nodeKey;
  /** 节点名称（可修改） */
  private String nodeName;
  /** 节点类型：start 原始数据 / label 标注 / review 审核 / end 合格数据 */
  private String nodeType;
  /** 流转顺序（升序） */
  private Integer sortOrder;
  /** 节点说明 */
  private String remark;
  /** 审核节点批注类型（JSON 数组文本，如 ["漏点","漏标"]；非审核节点为空） */
  private String commentTypes;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
  private Long createdBy;
  private Long updatedBy;
  private Integer deleted;
}
