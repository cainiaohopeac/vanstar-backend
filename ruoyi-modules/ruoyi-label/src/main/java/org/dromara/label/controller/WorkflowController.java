package org.dromara.label.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.dromara.label.common.Result;
import org.dromara.label.dto.WorkflowNodeSaveDTO;
import org.dromara.label.service.WorkflowService;
import org.dromara.label.vo.WorkflowNodeVO;
import org.dromara.label.vo.WorkflowTaskProgressVO;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 项目工作流域：项目工作流节点配置（项目详情「工作流配置」标签页）
 * 与任务工作流进度（任务详情「工作流」按钮）。
 */
@RestController
public class WorkflowController {

  @Autowired
  private WorkflowService workflowService;

  /** 项目工作流节点列表（未配置时自动初始化默认流程）。 */
  @GetMapping("/workflow/nodes")
  public Result<List<WorkflowNodeVO>> nodes(@RequestParam Long projectId) {
    return Result.success(workflowService.nodes(projectId));
  }

  /** 保存项目工作流配置（整体覆盖，按提交顺序重排流转顺序）。 */
  @Log(title = "工作流配置", businessType = BusinessType.UPDATE)
  @PostMapping("/workflow/nodes")
  public Result<List<WorkflowNodeVO>> saveNodes(@Valid @RequestBody WorkflowNodeSaveDTO dto) {
    return Result.success("工作流已保存", workflowService.saveNodes(dto));
  }

  /** 任务工作流进度（团队负责人 / 任务管理员在任务详情查看流程走向）。 */
  @GetMapping("/workflow/tasks/{taskId}/progress")
  public Result<WorkflowTaskProgressVO> taskProgress(@PathVariable Long taskId) {
    return Result.success(workflowService.taskProgress(taskId));
  }
}
