package org.dromara.label.dto;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * 项目工作流配置保存请求：整体覆盖该项目的节点列表（项目隔离）。
 */
@Data
public class WorkflowNodeSaveDTO implements Serializable {

  /** 所属项目ID */
  @NotNull(message = "请选择项目")
  private Long projectId;

  /** 节点列表（按提交顺序即流转顺序） */
  private List<WorkflowNodeItemDTO> nodes;
}
