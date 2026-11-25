package org.dromara.label.vo;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * 任务工作流进度 VO：任务当前所处节点 + 各节点完成情况。
 */
@Data
public class WorkflowTaskProgressVO implements Serializable {

  /** 任务ID */
  private Long taskId;
  /** 任务名称 */
  private String taskName;
  /** 任务状态 */
  private String taskStatus;
  /** 任务所属项目ID */
  private Long projectId;
  /** 任务配置的当前节点（驳回时仍为审核节点） */
  private String currentNodeKey;
  /** 任务当前有效节点（驳回后为标注节点，用于前端高亮「修订中」） */
  private String effectiveNodeKey;
  /** 当前有效节点名称 */
  private String currentNodeName;
  /** 当前有效节点说明 */
  private String description;
  /** 节点列表（含各节点状态） */
  private List<WorkflowTaskNodeVO> nodes;
}
