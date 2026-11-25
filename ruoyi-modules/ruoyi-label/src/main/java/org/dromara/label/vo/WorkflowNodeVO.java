package org.dromara.label.vo;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * 项目工作流节点 VO（项目详情「工作流配置」标签页 / 任务详情「工作流」弹窗展示）。
 */
@Data
public class WorkflowNodeVO implements Serializable {

  /** 节点ID（尚未落库时为 null） */
  private Long id;
  /** 所属项目ID */
  private Long projectId;
  /** 节点标识 */
  private String nodeKey;
  /** 节点名称 */
  private String nodeName;
  /** 节点类型：start/label/review/end */
  private String nodeType;
  /** 流转顺序（升序） */
  private Integer sortOrder;
  /** 节点说明 */
  private String remark;
  /** 是否固定节点（固定节点不可删除、不可改节点标识） */
  private Boolean fixed;
  /** 审核节点可选的批注类型（错误内容）；非审核节点为空列表 */
  private List<String> commentTypes;
}
