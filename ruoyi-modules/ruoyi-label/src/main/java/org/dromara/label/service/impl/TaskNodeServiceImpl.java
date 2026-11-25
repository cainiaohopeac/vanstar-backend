package org.dromara.label.service.impl;

import java.util.List;
import java.util.Map;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.label.common.LabelEntryHistoryAction;
import org.dromara.label.dto.EntryAssignDTO;
import org.dromara.label.dto.EntryReviewDTO;
import org.dromara.label.mapper.ReviewMapper;
import org.dromara.label.mapper.TaskMapper;
import org.dromara.label.service.BaseService;
import org.dromara.label.service.EntryHistoryService;
import org.dromara.label.service.EntryNodeService;
import org.dromara.label.service.ReviewService;
import org.dromara.label.service.TaskNodeService;
import org.dromara.label.service.TaskService;
import org.dromara.label.service.WorkflowService;
import org.dromara.label.vo.NodeClaimAutoVO;
import org.dromara.label.vo.TaskEntryVO;
import org.dromara.label.vo.TaskVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 任务条目按工作流节点统一操作实现。
 *
 * <p>标注节点仍复用 {@link TaskService} 的原有逻辑，避免破坏旧数据契约；
 * 审核节点领取直接委托 {@link ReviewService#claimEntries}（校验/审核状态/节点记录一体化），
 * 跳过/放弃在维护节点记录的同时释放审核领取，保证与审核工作台状态一致。</p>
 */
@Service
public class TaskNodeServiceImpl extends BaseService implements TaskNodeService {

  @Autowired
  private EntryNodeService entryNodeService;

  @Autowired
  private TaskService taskService;

  @Autowired
  private ReviewService reviewService;

  @Autowired
  private ReviewMapper reviewMapper;

  @Autowired
  private TaskMapper taskMapper;

  @Autowired
  private EntryHistoryService entryHistoryService;

  @Override
  @Transactional
  public TaskVO claim(Long taskId, List<Long> entryIds, String nodeKey, Long operatorId) {
    if (entryIds == null || entryIds.isEmpty()) {
      throw new ServiceException("请先勾选要领取的数据条目");
    }
    if (WorkflowService.KEY_LABEL.equals(nodeKey)) {
      EntryAssignDTO dto = new EntryAssignDTO();
      dto.setAssetIds(entryIds);
      dto.setAssigneeId(operatorId);
      dto.setMode(operatorId == null ? "claim" : "assign");
      return taskService.assignEntries(taskId, dto);
    }
    // 审核节点：复用条目级审核领取（成员校验 + 审核状态流转 + 节点记录一体化）。
    // nodeKey 一并传入：审批节点成员按自己所属节点校验资格并落节点记录，
    // 不受任务级有效节点（部分条目未提交时仍是 label）的限制。
    EntryReviewDTO dto = new EntryReviewDTO();
    dto.setAssetIds(entryIds);
    dto.setNodeKey(nodeKey);
    int count = reviewService.claimEntries(dto);
    writeOperationLog("NODE_CLAIM", "task", taskId,
        Map.of("nodeKey", nodeKey, "count", count));
    return taskService.task(taskId);
  }

  @Override
  @Transactional
  public NodeClaimAutoVO claimAuto(Long taskId, String nodeKey) {
    // 0) 先补齐该节点的池子记录：下面两条查询都是 JOIN label_entry_node ... n.status='pending'
    //    （INNER JOIN），无节点记录就查不到，会误报"当前节点暂无可领取的待审核数据"。
    ensureNodePool(taskId, nodeKey);
    // 1) 优先取"待审核"池子第一条（已标注/已修订 + 未审核），命中即返回
    TaskEntryVO first = taskMapper.selectFirstPendingNodeEntry(taskId, nodeKey);
    // 2) 兜底：待审核池子空时，取任意 pending 一条——允许审核员"先领取再处理"，
    //    避免上游出现"明明有未领取数据但点领取没反应"的体验断层
    if (first == null || first.getId() == null) {
      first = taskMapper.selectAnyFirstPendingNodeEntry(taskId, nodeKey);
    }
    if (first == null || first.getId() == null) {
      throw new ServiceException("当前节点暂无可领取的待审核数据");
    }
    // 复用单条领取：成员资格校验 + 审核状态流转（→审核中）+ 节点记录（→claimed）
    claim(taskId, List.of(first.getId()), nodeKey, null);
    return new NodeClaimAutoVO(first.getId(), first.getEntryName(), first.getMediaType());
  }

  /**
   * 补齐指定审核节点的条目池子记录（幂等：内部有「缺记录计数」守卫，已齐全时直接返回）。
   *
   * <p>背景：列表「总览」页签走 overview 端点，该端点明确不依赖也不初始化
   * {@code label_entry_node}；而节点操作（自动领取/跳过/放弃/恢复）都依赖该表
   * （INNER JOIN 或按 entryId+nodeKey 单查）。停在总览页签时表内没有记录，就会出现
   * 「明明有已标注条目却提示无可领取」「该条目在当前节点无状态记录」；
   * 手动勾选领取走 ensureNode 会逐条补建，因此表现为「先手动领一条之后就都正常了」。
   *
   * <p>标注节点不建审核类型记录，直接跳过。
   */
  private void ensureNodePool(Long taskId, String nodeKey) {
    if (taskId == null || nodeKey == null || nodeKey.isBlank()
        || WorkflowService.KEY_LABEL.equals(nodeKey)) {
      return;
    }
    entryNodeService.initNodeForTask(taskId, nodeKey, WorkflowService.NODE_TYPE_REVIEW);
  }

  @Override
  public TaskEntryVO locateMyClaimed(Long taskId, String nodeKey) {
    long userId = currentUserId();
    return taskMapper.selectMyFirstClaimedNodeEntry(taskId, userId, nodeKey);
  }

  @Override
  @Transactional
  public TaskVO skip(Long taskId, List<Long> entryIds, String nodeKey) {
    if (entryIds == null || entryIds.isEmpty()) {
      throw new ServiceException("请先勾选要跳过的数据条目");
    }
    if (WorkflowService.KEY_LABEL.equals(nodeKey)) {
      EntryAssignDTO dto = new EntryAssignDTO();
      dto.setAssetIds(entryIds);
      return taskService.skipEntries(taskId, dto);
    }
    ensureNodePool(taskId, nodeKey);
    long userId = currentUserId();
    for (Long entryId : entryIds) {
      entryNodeService.skip(entryId, nodeKey, userId);
    }
    // 释放审核领取：条目回到未审核，供其他审核员重新领取
    reviewMapper.releaseEntryReviewClaim(entryIds, userId);
    reviewMapper.syncAssetsFromEntries(entryIds);
    entryHistoryService.recordBatch(entryIds, LabelEntryHistoryAction.SKIP, "条目跳过", "审核", "审核节点");
    writeOperationLog("NODE_SKIP", "task", taskId,
        Map.of("nodeKey", nodeKey, "count", entryIds.size()));
    return taskService.task(taskId);
  }

  @Override
  @Transactional
  public TaskVO abandon(Long taskId, List<Long> entryIds, String nodeKey) {
    if (entryIds == null || entryIds.isEmpty()) {
      throw new ServiceException("请先勾选要放弃的数据条目");
    }
    if (WorkflowService.KEY_LABEL.equals(nodeKey)) {
      EntryAssignDTO dto = new EntryAssignDTO();
      dto.setAssetIds(entryIds);
      return taskService.abandonEntries(taskId, dto);
    }
    ensureNodePool(taskId, nodeKey);
    long userId = currentUserId();
    for (Long entryId : entryIds) {
      entryNodeService.abandon(entryId, nodeKey, userId);
    }
    // 释放审核领取：条目回到未审核，供其他审核员重新领取
    reviewMapper.releaseEntryReviewClaim(entryIds, userId);
    reviewMapper.syncAssetsFromEntries(entryIds);
    entryHistoryService.recordBatch(entryIds, LabelEntryHistoryAction.ABANDON, "条目放弃", "审核", "审核节点");
    writeOperationLog("NODE_ABANDON", "task", taskId,
        Map.of("nodeKey", nodeKey, "count", entryIds.size()));
    return taskService.task(taskId);
  }

  @Override
  @Transactional
  public TaskVO restore(Long taskId, List<Long> entryIds, String nodeKey) {
    if (entryIds == null || entryIds.isEmpty()) {
      throw new ServiceException("请先勾选要恢复的数据条目");
    }
    if (WorkflowService.KEY_LABEL.equals(nodeKey)) {
      EntryAssignDTO dto = new EntryAssignDTO();
      dto.setAssetIds(entryIds);
      return taskService.restoreEntries(taskId, dto);
    }
    ensureNodePool(taskId, nodeKey);
    for (Long entryId : entryIds) {
      entryNodeService.restore(entryId, nodeKey);
    }
    entryHistoryService.recordBatch(entryIds, LabelEntryHistoryAction.RESTORE, "条目恢复", "审核", "审核节点");
    writeOperationLog("NODE_RESTORE", "task", taskId,
        Map.of("nodeKey", nodeKey, "count", entryIds.size()));
    return taskService.task(taskId);
  }
}
