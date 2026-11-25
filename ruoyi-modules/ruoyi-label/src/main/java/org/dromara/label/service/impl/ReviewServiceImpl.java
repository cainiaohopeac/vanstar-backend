package org.dromara.label.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.label.common.LabelEntryHistoryAction;
import org.dromara.label.common.LabelEntryStatus;
import org.dromara.label.common.LabelJobStatus;
import org.dromara.label.common.LabelReviewRecord;
import org.dromara.label.common.LabelReviewStatus;
import org.dromara.label.common.PageResult;
import org.dromara.label.dto.EntryReviewDTO;
import org.dromara.label.dto.EntryReviewTargetDTO;
import org.dromara.label.dto.ReviewRejectDTO;
import org.dromara.label.mapper.ReviewMapper;
import org.dromara.label.service.BaseService;
import org.dromara.label.service.EntryHistoryService;
import org.dromara.label.service.EntryNodeService;
import org.dromara.label.service.ReviewService;
import org.dromara.label.service.TaskMemberService;
import org.dromara.label.service.TaskService;
import org.dromara.label.service.WorkflowService;
import org.dromara.label.vo.JobVO;
import org.dromara.label.vo.ReviewEntryVO;
import org.dromara.label.vo.ReviewRecordVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审核域服务：作业级审核/验收（兼容存量）、条目级审核（领取/通过/驳回、可多轮复审）、审核记录查询。
 * SQL 统一收敛至 ReviewMapper.xml。
 *
 * <p>{@code selectEntryReviewTargets} 返回 DTO 后，调用方直接按字段 getter 访问（编译期类型检查），
 * 删去本类自有的 {@code mapValue} 兼容方法（WorkflowServiceImpl / TaskServiceImpl 各自的同名私有方法不受影响）。</p>
 */
@Service
public class ReviewServiceImpl extends BaseService implements ReviewService {

  @Autowired
  private ReviewMapper reviewMapper;

  @Autowired
  private TaskService taskService;

  @Autowired
  private WorkflowService workflowService;

  @Autowired
  private TaskMemberService taskMemberService;

  @Autowired
  private EntryNodeService entryNodeService;

  @Autowired
  private EntryHistoryService entryHistoryService;

  /** 审核作业列表：仅包含审核/验收相关状态的作业。 */
  @Override
  public PageResult<JobVO> reviews(String status) {
    // 过滤下推 SQL：按放行状态集合查询，避免全量作业 JOIN 加载后内存过滤
    List<JobVO> filtered =
        taskService.jobsByFilter(
            null,
            List.of(
                LabelJobStatus.PENDING_REVIEW,
                LabelJobStatus.REVIEW_REJECTED,
                LabelJobStatus.PENDING_ACCEPTANCE,
                LabelJobStatus.COMPLETED),
            status);
    return PageResult.of(filtered);
  }

  /** 审核通过：作业进入待验收。 */
  @Override
  @Transactional
  public JobVO approveReview(Long jobId) {
    changeReviewState(jobId, LabelJobStatus.PENDING_ACCEPTANCE, LabelReviewRecord.RESULT_APPROVED,
        "审核通过，进入验收");
    // 审核通过不修改标注状态，仅把审核状态置为已通过
    reviewMapper.updateAssetsReviewByJob(
        jobId, LabelReviewStatus.PASSED, null, false, currentUserId(), currentUserId());
    reviewMapper.syncEntriesFromAssetsByJob(jobId, currentUserId());
    taskService.syncTaskStatus(reviewMapper.selectJobTaskId(jobId));
    return taskService.job(jobId);
  }

  /** 审核退回：作业退回标注并记录退回意见。 */
  @Override
  @Transactional
  public JobVO rejectReview(Long jobId, ReviewRejectDTO dto) {
    String comment = dto.getComment() == null || dto.getComment().isBlank() ? "请返修" : dto.getComment();
    reviewMapper.updateJobRejected(jobId, currentUserId());
    // 审核驳回：标注状态回到未修订，审核状态记为审核不通过，不通过次数+1
    reviewMapper.updateAssetsReviewByJob(
        jobId, LabelReviewStatus.REJECTED, LabelEntryStatus.UNREVISED, true, currentUserId(), currentUserId());
    reviewMapper.syncEntriesFromAssetsByJob(jobId, currentUserId());
    addReviewRecord(jobId, LabelReviewRecord.TYPE_REVIEW, LabelReviewRecord.RESULT_REJECTED,
        comment);
    createNotification(findAssigneeId(jobId), "审核退回", comment, "job", jobId);
    writeOperationLog("REJECT_JOB", "job", jobId, Map.of("comment", comment));
    taskService.syncTaskStatus(reviewMapper.selectJobTaskId(jobId));
    return taskService.job(jobId);
  }

  /** 验收通过：作业完成入库并累计任务完成数。 */
  @Override
  @Transactional
  public JobVO acceptReview(Long jobId) {
    changeReviewState(jobId, LabelJobStatus.COMPLETED, LabelReviewRecord.RESULT_ACCEPTED,
        "验收通过并入库");
    // 验收通过同样只更新审核状态，标注状态保持已标注/已修订
    reviewMapper.updateAssetsReviewByJob(
        jobId, LabelReviewStatus.PASSED, null, false, currentUserId(), currentUserId());
    reviewMapper.syncEntriesFromAssetsByJob(jobId, currentUserId());
    reviewMapper.updateTaskCompletedCountByJob(jobId);
    taskService.syncTaskStatus(reviewMapper.selectJobTaskId(jobId));
    return taskService.job(jobId);
  }

  /** 作业审核/验收记录。 */
  @Override
  public List<ReviewRecordVO> reviewRecords(Long jobId) {
    return reviewMapper.selectReviewRecords(jobId);
  }

  // ---------------- 审核流转辅助 ----------------

  /** 变更作业审核状态（通过/验收共用），并写入审核记录与操作日志。 */
  private void changeReviewState(Long jobId, String jobStatus, String result, String comment) {
    reviewMapper.updateJobReviewState(jobId, jobStatus, result, currentUserId());
    addReviewRecord(
        jobId,
        LabelJobStatus.PENDING_ACCEPTANCE.equals(jobStatus)
            ? LabelReviewRecord.TYPE_REVIEW
            : LabelReviewRecord.TYPE_ACCEPTANCE,
        result,
        comment);
    writeOperationLog(
        result.toUpperCase(Locale.ROOT) + "_JOB", "job", jobId, Map.of("comment", comment));
  }

  private void addReviewRecord(Long jobId, String reviewType, String result, String comment) {
    long userId = currentUserId();
    Map<String, Object> p = new HashMap<>();
    p.put("jobId", jobId);
    p.put("assetId", firstAssetId(jobId));
    p.put("entryId", null);
    p.put("annotationId", reviewMapper.selectLatestAnnotationIdByJob(jobId));
    p.put("reviewerId", userId);
    p.put("reviewType", reviewType);
    p.put("result", result);
    p.put("comment", comment);
    p.put("userId", userId);
    reviewMapper.insertReviewRecord(p);
  }

  // ---------------- 条目级审核（领取/通过/驳回，可多轮复审） ----------------

  /** 条目级审核列表（跨任务，按审核状态页签过滤）。 */
  @Override
  public PageResult<ReviewEntryVO> reviewEntries(
      Long taskId, String status, String keyword, int pageNum, int pageSize) {
    long userId = currentUserId();
    int size = Math.max(1, pageSize);
    int offset = Math.max(0, (Math.max(1, pageNum) - 1) * size);
    long total = reviewMapper.countReviewEntries(taskId, status, keyword, userId);
    List<ReviewEntryVO> list =
        reviewMapper.selectReviewEntries(taskId, status, keyword, userId, offset, size);
    return new PageResult<>(total, list);
  }

  /** 领取审核：仅「已标注/已修订且未审核」的条目可领取，领取即 审核中/复审中。 */
  @Override
  @Transactional
  public int claimEntries(EntryReviewDTO dto) {
    List<Long> assetIds = normalizeAssetIds(dto);
    List<EntryReviewTargetDTO> targets = reviewMapper.selectEntryReviewTargets(assetIds);
    if (targets.size() != assetIds.size()) {
      throw new ServiceException("存在无效的数据条目，领取已终止");
    }
    for (EntryReviewTargetDTO target : targets) {
      String reviewStatus = target.getReviewStatus();
      String labelStatus = target.getLabelStatus();
      boolean claimable = LabelReviewStatus.NONE.equals(reviewStatus)
          && (LabelEntryStatus.LABELED.equals(labelStatus)
              || LabelEntryStatus.REVISED.equals(labelStatus));
      if (!claimable) {
        throw new ServiceException("仅「已标注/已修订且未审核」的条目可以领取审核");
      }
    }
    long userId = currentUserId();
    // 工作流节点校验：节点视图领取（dto 带 nodeKey=review_N）时按该节点校验成员归属；
    // 未指定节点（「我的审核」跨任务入口）时保持任务级有效节点口径（管理员/负责人不受节点限制）。
    // 不能一律用任务级有效节点：任务中部分条目仍在标注时有效节点仍是 label，
    // 而已提交审核的条目已流转到审批节点，审批节点成员按节点领取不应被任务级节点拦截。
    String claimNodeKey = dto.getNodeKey();
    for (Long taskId : taskIdsOf(targets)) {
      String checkNodeKey = (claimNodeKey == null || claimNodeKey.isBlank())
          ? workflowService.effectiveNodeKey(taskId)
          : claimNodeKey.trim();
      taskMemberService.assertClaimable(taskId, userId, checkNodeKey);
    }
    int count = reviewMapper.updateEntryReviewClaim(assetIds, userId);
    if (count <= 0) {
      throw new ServiceException("没有可领取的条目，请刷新后重试");
    }
    // 条目的审核状态同步到包内数据资产
    reviewMapper.syncAssetsFromEntries(assetIds);
    // 同步节点领取状态：节点视图领取时按指定的审核节点记录 claimed；
    // 未指定节点时按任务当前有效节点标记（任务处于驳回修订阶段时有效节点为 label，
    // 不产生审核节点记录，避免脏数据）
    for (EntryReviewTargetDTO target : targets) {
      Long entryId = target.getEntryId();
      Long taskId = target.getTaskId();
      if (entryId == null || taskId == null) {
        continue;
      }
      String nodeKey = (claimNodeKey == null || claimNodeKey.isBlank())
          ? workflowService.effectiveNodeKey(taskId)
          : claimNodeKey.trim();
      if (!WorkflowService.KEY_LABEL.equals(nodeKey)) {
        entryNodeService.ensureNode(entryId, taskId, nodeKey, WorkflowService.NODE_TYPE_REVIEW);
        entryNodeService.claim(entryId, nodeKey, userId);
      }
    }
    writeOperationLog("CLAIM_REVIEW_ENTRIES", "asset", assetIds.get(0), Map.of("count", count));
    entryHistoryService.recordBatch(assetIds, LabelEntryHistoryAction.REVIEW_CLAIM, "条目领取审核", "审核", null);
    return count;
  }

  /** 审核通过：标注状态保持不变，仅审核状态置为已通过。 */
  @Override
  @Transactional
  public int approveEntries(EntryReviewDTO dto) {
    List<Long> assetIds = normalizeAssetIds(dto);
    List<EntryReviewTargetDTO> targets = assertInReviewing(assetIds);
    int count = reviewMapper.updateEntryReviewApprove(assetIds, currentUserId());
    if (count <= 0) {
      throw new ServiceException("没有可审核通过的条目，请刷新后重试");
    }
    reviewMapper.syncAssetsFromEntries(assetIds);
    // 同步节点状态：当前审核节点完成（按条目定位其被领取的节点记录，避免任务级节点推导错位）
    for (EntryReviewTargetDTO target : targets) {
      Long entryId = target.getEntryId();
      if (entryId != null) {
        entryNodeService.markDoneClaimed(entryId);
      }
      notifyEntryReview(target, "审核通过", "审核通过");
      addEntryReviewRecord(target, LabelReviewRecord.TYPE_REVIEW, LabelReviewRecord.RESULT_PASSED,
          "审核通过");
    }
    // 同步任务状态与工作流节点：本轮全部通过则推进到下一审核节点，最后一个节点通过即产出合格数据
    syncTaskStatusOfTargets(targets);
    writeOperationLog("APPROVE_REVIEW_ENTRIES", "asset", assetIds.get(0), Map.of("count", count));
    entryHistoryService.recordBatch(assetIds, LabelEntryHistoryAction.REVIEW_PASS, "条目通过", "审核", null);
    return count;
  }

  /** 审核驳回：标注状态回到「未修订」等待领取修订；复审驳回记为「复审不通过」。 */
  @Override
  @Transactional
  public int rejectEntries(EntryReviewDTO dto) {
    List<Long> assetIds = normalizeAssetIds(dto);
    List<EntryReviewTargetDTO> targets = assertInReviewing(assetIds);
    String comment =
        dto.getComment() == null || dto.getComment().isBlank() ? "请返修" : dto.getComment();
    int count = reviewMapper.updateEntryReviewReject(assetIds, currentUserId());
    if (count <= 0) {
      throw new ServiceException("没有可驳回的条目，请刷新后重试");
    }
    reviewMapper.syncAssetsFromEntries(assetIds);
    // 相关作业退回标注（已退回），标注员可在工作台重新修订
    taskService.markEntriesReturned(assetIds);
    // 同步节点状态：驳回后条目修订完会重新回到审核节点，节点记录回池子等待再次领取
    for (EntryReviewTargetDTO target : targets) {
      Long entryId = target.getEntryId();
      if (entryId != null) {
        entryNodeService.resetPendingClaimed(entryId);
      }
    }
    for (EntryReviewTargetDTO target : targets) {
      boolean reReview = LabelReviewStatus.RE_REVIEWING.equals(target.getReviewStatus());
      notifyEntryReview(target, reReview ? "复审驳回" : "审核退回", comment);
      addEntryReviewRecord(
          target,
          reReview ? LabelReviewRecord.TYPE_RE_REVIEW : LabelReviewRecord.TYPE_REVIEW,
          LabelReviewRecord.RESULT_REJECTED,
          comment);
    }
    // 驳回后任务回到标注节点修订（任务状态 review_rejected，有效节点为标注）
    syncTaskStatusOfTargets(targets);
    writeOperationLog("REJECT_REVIEW_ENTRIES", "asset", assetIds.get(0),
        Map.of("count", count, "comment", comment));
    entryHistoryService.recordBatch(assetIds, LabelEntryHistoryAction.REVIEW_REJECT, "条目驳回", "审核", comment);
    return count;
  }

  /** 审核目标涉及的（去重后的）任务ID集合。 */
  private Set<Long> taskIdsOf(List<EntryReviewTargetDTO> targets) {
    Set<Long> taskIds = new LinkedHashSet<>();
    if (targets == null) {
      return taskIds;
    }
    for (EntryReviewTargetDTO target : targets) {
      Long taskId = target.getTaskId();
      if (taskId != null) {
        taskIds.add(taskId);
      }
    }
    return taskIds;
  }

  /** 审核结果落库后按任务刷新状态与工作流节点。 */
  private void syncTaskStatusOfTargets(List<EntryReviewTargetDTO> targets) {
    for (Long taskId : taskIdsOf(targets)) {
      taskService.syncTaskStatus(taskId);
    }
  }

  /** 单个条目（数据集合包）的审核历史。 */
  @Override
  public List<ReviewRecordVO> entryReviewRecords(Long entryId) {
    return reviewMapper.selectReviewRecordsByAsset(entryId);
  }

  /**
   * 条目级审核结果通知：优先带作业跳转（工作台可按作业打开），
   * 无关联作业时退化为按条目标识通知。
   */
  private void notifyEntryReview(EntryReviewTargetDTO target, String title, String content) {
    Long entryId = target.getEntryId();
    Long jobId = target.getJobId();
    String entryName = stringValue(target.getEntryName());
    String text = content;
    if (!entryName.isBlank()) {
      text = "条目「" + entryName + "」" + content;
    }
    if (jobId != null && jobId > 0) {
      createNotification(target.getAssigneeId(), title, text, "job", jobId);
    } else {
      createNotification(target.getAssigneeId(), title, text, "asset", entryId);
    }
  }

  /** 校验选中的条目均处于「审核中/复审中」且由本人（或超管）领取。 */
  private List<EntryReviewTargetDTO> assertInReviewing(List<Long> assetIds) {
    List<EntryReviewTargetDTO> targets = reviewMapper.selectEntryReviewTargets(assetIds);
    if (targets.size() != assetIds.size()) {
      throw new ServiceException("存在无效的数据条目，操作已终止");
    }
    long userId = currentUserId();
    boolean superAdmin = isSuperAdmin(userId);
    for (EntryReviewTargetDTO target : targets) {
      String reviewStatus = target.getReviewStatus();
      if (!LabelReviewStatus.REVIEWING.equals(reviewStatus)
          && !LabelReviewStatus.RE_REVIEWING.equals(reviewStatus)) {
        throw new ServiceException("仅「审核中/复审中」的条目可以审核");
      }
      Long reviewerId = target.getReviewerId();
      if (!superAdmin && reviewerId != null && !reviewerId.equals(userId)) {
        throw new ServiceException("该条目已由他人领取审核，无法操作");
      }
    }
    return targets;
  }

  /** 写入条目级审核记录（job_id 缺失时记 0，兼容存量表结构）。 */
  private void addEntryReviewRecord(
      EntryReviewTargetDTO target, String reviewType, String result, String comment) {
    long userId = currentUserId();
    Long jobId = target.getJobId();
    Map<String, Object> p = new HashMap<>();
    p.put("jobId", jobId == null ? 0L : jobId);
    p.put("assetId", null);
    p.put("entryId", target.getEntryId());
    p.put("annotationId", null);
    p.put("reviewerId", userId);
    p.put("reviewType", reviewType);
    p.put("result", result);
    p.put("comment", comment);
    p.put("userId", userId);
    reviewMapper.insertReviewRecord(p);
  }

  /** 归一化并校验条目ID集合。 */
  private List<Long> normalizeAssetIds(EntryReviewDTO dto) {
    List<Long> assetIds = dto.getAssetIds() == null
        ? new ArrayList<>()
        : dto.getAssetIds().stream()
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toCollection(ArrayList::new));
    if (assetIds.isEmpty()) {
      throw new ServiceException("请先勾选要审核的数据条目");
    }
    return assetIds;
  }

  /** 是否超管（超管不受「本人领取」限制）。 */
  private boolean isSuperAdmin(long userId) {
    try {
      return LoginHelper.isSuperAdmin(userId);
    } catch (Exception e) {
      return false;
    }
  }
}