package org.dromara.label.vo;

import java.io.Serializable;
import lombok.Data;

/**
 * 任务在工作流中的单个节点状态（任务详情「工作流」按钮弹窗）。
 */
@Data
public class WorkflowTaskNodeVO implements Serializable {

  /** 节点标识 */
  private String nodeKey;
  /** 节点名称 */
  private String nodeName;
  /** 节点类型：start/label/review/end */
  private String nodeType;
  /** 流转顺序 */
  private Integer sortOrder;
  /** 节点状态：done 已完成 / current 进行中 / todo 未开始 */
  private String state;
  /** 状态说明（如「已完成」「审核中」「待审核」「未开始」） */
  private String description;
  /** 该节点关联的条目总数（统计值，便于了解进度） */
  private Long total;
  /** 该节点已处理完成的条目数 */
  private Long doneCount;
}
