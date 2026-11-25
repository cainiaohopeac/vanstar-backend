package org.dromara.label.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.dromara.label.common.PageResult;
import org.dromara.label.common.Result;
import org.dromara.label.dto.EntryAssignDTO;
import org.dromara.label.dto.JobAssignDTO;
import org.dromara.label.dto.JobStatusDTO;
import org.dromara.label.dto.TaskEntryListQueryDTO;
import org.dromara.label.dto.TaskOverviewEntriesDTO;
import org.dromara.label.dto.TaskQueryDTO;
import org.dromara.label.dto.TaskSaveDTO;
import org.dromara.label.service.EntryHistoryService;
import org.dromara.label.service.TaskNodeService;
import org.dromara.label.service.TaskService;
import org.dromara.label.vo.AssetBatchVO;
import org.dromara.label.vo.AssetVO;
import org.dromara.label.vo.BatchOccupancyVO;
import org.dromara.label.vo.EntryHistoryVO;
import org.dromara.label.vo.EntryJobVO;
import org.dromara.label.vo.JobVO;
import org.dromara.label.vo.NodeClaimAutoVO;
import org.dromara.label.vo.TaskBatchAssignVO;
import org.dromara.label.vo.TaskClaimAutoVO;
import org.dromara.label.vo.TaskEntrySummaryVO;
import org.dromara.label.vo.TaskEntryVO;
import org.dromara.label.vo.TaskOverviewEntriesSummaryVO;
import org.dromara.label.vo.TaskOverviewEntryVO;
import org.dromara.label.vo.TaskPublishVO;
import org.dromara.label.vo.TaskSplitVO;
import org.dromara.label.vo.TaskVO;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 任务域（任务生命周期/拆分/发布、作业列表与详情、作业分配与状态）。 */
@RestController
public class TaskController {

  @Autowired
  private TaskService taskService;

  @Autowired
  private TaskNodeService taskNodeService;

  @Autowired
  private EntryHistoryService entryHistoryService;

  /** 任务列表分页查询（POST：查询条件以请求体 TaskQueryDTO 传递）。 */
  @PostMapping("/tasks/list")
  public Result<PageResult<TaskVO>> tasks(@RequestBody @Valid TaskQueryDTO dto) {
    return Result.success(taskService.tasks(dto));
  }

  @Log(title = "任务管理", businessType = BusinessType.INSERT)
  @PostMapping("/tasks/create")
  public Result<TaskVO> createTask(@RequestBody TaskSaveDTO dto) {
    return Result.success("创建成功", taskService.createTask(dto));
  }

  @GetMapping("/tasks/{id}")
  public Result<TaskVO> task(@PathVariable Long id) {
    return Result.success(taskService.task(id));
  }

  /** 项目下已被任务下发的批次（同一批次只允许下发一次，前端据此禁用已下发选项）。 */
  @GetMapping("/tasks/batches/occupancy")
  public Result<List<BatchOccupancyVO>> batchOccupancy(@RequestParam("projectId") Long projectId) {
    return Result.success(taskService.batchOccupancy(projectId));
  }

  @Log(title = "任务管理", businessType = BusinessType.UPDATE)
  @PutMapping("/tasks/update")
  public Result<TaskVO> updateTask(@RequestBody TaskSaveDTO dto) {
    if (dto.getId() == null) {
      return Result.error("任务ID不能为空");
    }
    return Result.success("保存成功", taskService.updateTask(dto));
  }

  @Log(title = "任务拆分", businessType = BusinessType.OTHER)
  @PostMapping("/tasks/{id}/split")
  public Result<TaskSplitVO> splitTask(@PathVariable Long id) {
    return Result.success("拆分完成", taskService.splitTask(id));
  }

  @Log(title = "任务发布", businessType = BusinessType.UPDATE)
  @PostMapping("/tasks/{id}/publish")
  public Result<TaskPublishVO> publishTask(@PathVariable Long id) {
    return Result.success("发布成功", taskService.publishTask(id));
  }

  @GetMapping("/tasks/{id}/jobs")
  public Result<List<JobVO>> taskJobs(@PathVariable Long id) {
    return Result.success(taskService.jobs(id));
  }

  @GetMapping("/tasks/{id}/batches")
  public Result<PageResult<AssetBatchVO>> taskBatches(
      @PathVariable Long id,
      @RequestParam(value = "scope", required = false) String scope) {
    return Result.success(taskService.taskBatches(id, scope));
  }

  @Log(title = "任务批次分配", businessType = BusinessType.UPDATE)
  @PostMapping("/tasks/{id}/batches/{batchId}/assign")
  public Result<TaskBatchAssignVO> assignBatch(@PathVariable Long id, @PathVariable Long batchId) {
    return Result.success("分配成功", taskService.assignBatch(id, batchId));
  }

  @GetMapping("/tasks/{id}/appendable-batches")
  public Result<List<AssetBatchVO>> appendableBatches(@PathVariable Long id) {
    return Result.success(taskService.appendableBatches(id));
  }

  @Log(title = "任务追加批次", businessType = BusinessType.UPDATE)
  @PostMapping("/tasks/{id}/batches/append")
  public Result<TaskVO> appendBatches(@PathVariable Long id, @RequestBody TaskSaveDTO dto) {
    return Result.success("已追加批次", taskService.appendBatches(id, dto));
  }

  @GetMapping("/tasks/{id}/items")
  public Result<PageResult<AssetVO>> taskItems(@PathVariable Long id) {
    return Result.success(taskService.taskItems(id));
  }

  // ---------------- 任务数据条目（条目级分配） ----------------

  /** 当前用户负责的团队名称；非空时任务列表只展示这些团队接收到的任务。 */
  @GetMapping("/tasks/leader-teams")
  public Result<List<String>> leaderTeams() {
    return Result.success(taskService.myLeaderTeams());
  }

  /** 当前用户负责的团队ID；用于把条目分配范围限定在本团队内。 */
  @GetMapping("/tasks/leader-team-ids")
  public Result<List<Long>> leaderTeamIds() {
    return Result.success(taskService.myLeaderTeamIds());
  }

  /** 任务条目分页：taskId 通过 body 必填；按批次/页签/关键字过滤；scope=myteam 只看本团队范围。 */
  @PostMapping("/tasks/entries")
  public Result<PageResult<TaskEntryVO>> taskEntries(@RequestBody @Valid TaskEntryListQueryDTO dto) {
    return Result.success(taskService.entries(dto));
  }

  /** 某条目的操作历史（任务详情「条目历史」弹窗用），按时间正序返回。 */
  @GetMapping("/tasks/{id}/entries/{entryId}/history")
  public Result<List<EntryHistoryVO>> entryHistory(@PathVariable Long id,
      @PathVariable Long entryId) {
    return Result.success(entryHistoryService.listByEntryId(entryId));
  }

  /** 任务条目状态统计（总览/待分配/已跳过/未修订/已放弃 页签计数）；scope=myteam 按团队范围过滤。batchId 必填（任务一定有至少一个批次）。 */
  @GetMapping("/tasks/{id}/entries/summary")
  public Result<TaskEntrySummaryVO> taskEntrySummary(
      @PathVariable Long id,
      @RequestParam("batchId") Long batchId,
      @RequestParam(value = "scope", required = false) String scope,
      @RequestParam(value = "nodeKey", required = false) String nodeKey) {
    return Result.success(taskService.entrySummary(id, batchId, scope, nodeKey));
  }

  /**
   * 任务详情「总览」分页（跨节点聚合）：该 task+batch 下的条目（不限节点类型）。
   * 可见性按角色分叉：团队负责人 / 任务管理员 / 超管看全部条目，普通成员仅看本人参与过的条目。
   */
  @PostMapping("/tasks/overview-entries")
  public Result<PageResult<TaskOverviewEntryVO>> overviewEntries(
      @RequestBody @Valid TaskOverviewEntriesDTO dto) {
    return Result.success(taskService.overviewEntries(dto));
  }

  /**
   * 任务详情「总览」状态分布统计（pending / claimed / skipped / abandoned / done），与列表同口径。
   * batchId 可空，为空时跨 batch 聚合。
   */
  @GetMapping("/tasks/{id}/overview-entries/summary")
  public Result<TaskOverviewEntriesSummaryVO> overviewEntriesSummary(
      @PathVariable Long id,
      @RequestParam(value = "batchId", required = false) Long batchId) {
    return Result.success(taskService.overviewEntriesSummary(id, batchId));
  }

  /** 条目级分配：mode=claim 领取标注意味着把自己设为标注员；mode=assign 分配给他人（仅团队负责人）。 */
  @Log(title = "任务条目分配", businessType = BusinessType.GRANT)
  @PostMapping("/tasks/{id}/entries/assign")
  public Result<TaskVO> assignEntries(@PathVariable Long id, @RequestBody EntryAssignDTO dto) {
    return Result.success("分配成功", taskService.assignEntries(id, dto));
  }

  /**
   * 自动领取标注（未勾选条目时）：本人有待办进入第一条待办，
   * 无待办则在任务池内随机领取一条待分配条目并直接开始标注。
   */
  @Log(title = "任务条目领取", businessType = BusinessType.UPDATE)
  @PostMapping("/tasks/{id}/entries/claim-auto")
  public Result<TaskClaimAutoVO> claimAutoEntry(@PathVariable Long id,
      @RequestParam(value = "batchId", required = false) Long batchId) {
    return Result.success("领取成功", taskService.claimAutoEntry(id, batchId));
  }

  /** 批量查询条目归属作业ID（任务详情「查看」多选/全选时按作业分组打开只读工作台） */
  @GetMapping("/tasks/{id}/entries/job-ids")
  public Result<List<EntryJobVO>> entryJobIds(@PathVariable Long id,
      @RequestParam("entryIds") List<Long> entryIds) {
    return Result.success(taskService.entryJobIds(id, entryIds));
  }

  /** 标注员跳过：把选中条目置为「已跳过」，等待管理员重新分配。 */
  @Log(title = "任务条目跳过", businessType = BusinessType.UPDATE)
  @PostMapping("/tasks/{id}/entries/skip")
  public Result<TaskVO> skipEntries(@PathVariable Long id, @RequestBody EntryAssignDTO dto) {
    return Result.success("已跳过", taskService.skipEntries(id, dto));
  }

  /** 领取修订：把选中「未修订」条目领取为自己修订，进入「修订中」。 */
  @Log(title = "任务条目修订领取", businessType = BusinessType.UPDATE)
  @PostMapping("/tasks/{id}/entries/revise")
  public Result<TaskVO> claimReviseEntries(@PathVariable Long id, @RequestBody EntryAssignDTO dto) {
    return Result.success("领取修订成功", taskService.claimReviseEntries(id, dto));
  }

  /** 标注员放弃：把选中「标注中/修订中」条目置为「已放弃」，团队内可见，仅放弃人可恢复。 */
  @Log(title = "任务条目放弃", businessType = BusinessType.UPDATE)
  @PostMapping("/tasks/{id}/entries/abandon")
  public Result<TaskVO> abandonEntries(@PathVariable Long id, @RequestBody EntryAssignDTO dto) {
    return Result.success("已放弃", taskService.abandonEntries(id, dto));
  }

  /** 恢复已放弃条目：仅放弃人本人可恢复，标注状态回滚到放弃前状态。 */
  @Log(title = "任务条目恢复", businessType = BusinessType.UPDATE)
  @PostMapping("/tasks/{id}/entries/restore")
  public Result<TaskVO> restoreEntries(@PathVariable Long id, @RequestBody EntryAssignDTO dto) {
    return Result.success("已恢复", taskService.restoreEntries(id, dto));
  }

  /** 释放领取（仅团队负责人/任务管理员）：把已领取、进行中的条目退回未领取，状态自动回退（标注中→未标注、审核中→未审核）。 */
  @Log(title = "任务条目释放", businessType = BusinessType.UPDATE)
  @PostMapping("/tasks/{id}/entries/release")
  public Result<TaskVO> releaseEntries(@PathVariable Long id, @RequestBody EntryAssignDTO dto) {
    return Result.success("已释放", taskService.releaseEntries(id, dto));
  }

  /** 定位条目所属作业，用于任务详情页「查看 / 预览标注」跳转标注工作台。 */
  @GetMapping("/tasks/{id}/entries/{assetId}/job")
  public Result<Long> entryJob(@PathVariable Long id, @PathVariable Long assetId) {
    return Result.success(taskService.entryJobId(id, assetId));
  }

  // ---------------- 按工作流节点统一操作条目（池子/待办/跳过/放弃） ----------------

  /** 节点领取/分配：nodeKey=label 复用原标注逻辑，nodeKey=review_N 走 label_entry_node。 */
  @Log(title = "节点条目领取", businessType = BusinessType.UPDATE)
  @PostMapping("/tasks/{id}/node-entries/claim")
  public Result<TaskVO> claimNodeEntries(@PathVariable Long id,
      @RequestParam("nodeKey") String nodeKey,
      @RequestParam(value = "operatorId", required = false) Long operatorId,
      @RequestBody EntryAssignDTO dto) {
    return Result.success("领取成功", taskNodeService.claim(id, dto.getAssetIds(), nodeKey, operatorId));
  }

  /** 节点自动领取：未勾选条目时领取该节点池子第一条待审核数据（→审核中），返回条目信息供直接进入审核。 */
  @Log(title = "节点条目领取", businessType = BusinessType.UPDATE)
  @PostMapping("/tasks/{id}/node-entries/claim-auto")
  public Result<NodeClaimAutoVO> claimAutoNodeEntry(@PathVariable Long id,
      @RequestParam("nodeKey") String nodeKey) {
    return Result.success("领取成功", taskNodeService.claimAuto(id, nodeKey));
  }

  /**
   * 节点领取按钮专用入口：定位当前登录人在该任务该节点下已领取（status='claimed'）的第一条
   * 待审核条目（按 n.claim_time ASC 升序：先领的先审）。仅做查询，不做领取动作。
   * 解决「当前 tab 切到非 claimed 时，按钮点了没反应」的问题。
   */
  @GetMapping("/tasks/{id}/node-entries/my-claimed")
  public Result<TaskEntryVO> locateMyClaimed(@PathVariable Long id,
      @RequestParam("nodeKey") String nodeKey) {
    return Result.success(taskNodeService.locateMyClaimed(id, nodeKey));
  }

  @Log(title = "节点条目跳过", businessType = BusinessType.UPDATE)
  @PostMapping("/tasks/{id}/node-entries/skip")
  public Result<TaskVO> skipNodeEntries(@PathVariable Long id,
      @RequestParam("nodeKey") String nodeKey,
      @RequestBody EntryAssignDTO dto) {
    return Result.success("已跳过", taskNodeService.skip(id, dto.getAssetIds(), nodeKey));
  }

  @Log(title = "节点条目放弃", businessType = BusinessType.UPDATE)
  @PostMapping("/tasks/{id}/node-entries/abandon")
  public Result<TaskVO> abandonNodeEntries(@PathVariable Long id,
      @RequestParam("nodeKey") String nodeKey,
      @RequestBody EntryAssignDTO dto) {
    return Result.success("已放弃", taskNodeService.abandon(id, dto.getAssetIds(), nodeKey));
  }

  @Log(title = "节点条目恢复", businessType = BusinessType.UPDATE)
  @PostMapping("/tasks/{id}/node-entries/restore")
  public Result<TaskVO> restoreNodeEntries(@PathVariable Long id,
      @RequestParam("nodeKey") String nodeKey,
      @RequestBody EntryAssignDTO dto) {
    return Result.success("已恢复", taskNodeService.restore(id, dto.getAssetIds(), nodeKey));
  }

  // ---------------- 作业 ----------------

  @GetMapping("/jobs/{id}")
  public Result<JobVO> job(@PathVariable Long id) {
    return Result.success(taskService.job(id));
  }

  @Log(title = "作业分配", businessType = BusinessType.GRANT)
  @PutMapping("/jobs/{id}/assign")
  public Result<JobVO> assignJob(@PathVariable Long id, @RequestBody JobAssignDTO dto) {
    return Result.success("分配成功", taskService.assignJob(id, dto));
  }

  @Log(title = "作业重新分配", businessType = BusinessType.GRANT)
  @PutMapping("/jobs/{id}/reassign")
  public Result<JobVO> reassignJob(@PathVariable Long id, @RequestBody JobAssignDTO dto) {
    return Result.success("重新分配成功", taskService.assignJob(id, dto));
  }

  @Log(title = "作业状态", businessType = BusinessType.UPDATE)
  @PutMapping("/jobs/{id}/status")
  public Result<JobVO> updateJobStatus(@PathVariable Long id, @RequestBody JobStatusDTO dto) {
    return Result.success("状态已更新", taskService.updateJobStatus(id, dto));
  }

}
