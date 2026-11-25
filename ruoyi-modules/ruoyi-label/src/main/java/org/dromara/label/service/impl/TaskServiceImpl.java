package org.dromara.label.service.impl;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.label.common.EntryNodeStatus;
import org.dromara.label.common.LabelEntryHistoryAction;
import org.dromara.label.common.LabelEntryStatus;
import org.dromara.label.common.LabelJobStatus;
import org.dromara.label.common.LabelReviewStatus;
import org.dromara.label.common.MediaType;
import org.dromara.label.common.PageResult;
import org.dromara.label.common.ProjectStatus;
import org.dromara.label.common.TaskAssignMode;
import org.dromara.label.common.TaskScope;
import org.dromara.label.dto.EntryAssignDTO;
import org.dromara.label.dto.JobAssignDTO;
import org.dromara.label.dto.JobStatusDTO;
import org.dromara.label.dto.TaskEntryListQueryDTO;
import org.dromara.label.dto.TaskOverviewEntriesDTO;
import org.dromara.label.dto.TaskQueryDTO;
import org.dromara.label.dto.TaskSaveDTO;
import org.dromara.label.entity.LabelEntryNode;
import org.dromara.label.entity.LabelTask;
import org.dromara.label.entity.LabelWorkflowNode;
import org.dromara.label.mapper.EntryNodeMapper;
import org.dromara.label.mapper.ProjectMapper;
import org.dromara.label.mapper.TaskMapper;
import org.dromara.label.mapper.WorkflowNodeMapper;
import org.dromara.label.service.BaseService;
import org.dromara.label.service.EntryHistoryService;
import org.dromara.label.service.EntryNodeService;
import org.dromara.label.service.ProjectService;
import org.dromara.label.service.TaskMemberService;
import org.dromara.label.service.TaskService;
import org.dromara.label.service.WorkflowService;
import org.dromara.label.vo.AssetBatchVO;
import org.dromara.label.vo.AssetVO;
import org.dromara.label.vo.BatchOccupancyVO;
import org.dromara.label.vo.EntryJobVO;
import org.dromara.label.vo.JobVO;
import org.dromara.label.vo.TaskBatchAssignVO;
import org.dromara.label.vo.TaskClaimAutoVO;
import org.dromara.label.vo.TaskEntrySummaryVO;
import org.dromara.label.vo.TaskEntryVO;
import org.dromara.label.vo.TaskOverviewEntriesSummaryVO;
import org.dromara.label.vo.TaskOverviewEntryVO;
import org.dromara.label.vo.TaskPublishVO;
import org.dromara.label.vo.TaskSplitVO;
import org.dromara.label.vo.TaskVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 任务域服务：任务创建/编辑/拆分/发布/收藏、作业(job)列表与详情、作业分配、
 * 任务进度聚合刷新。SQL 统一收敛至 TaskMapper.xml。
 */
@Service
public class TaskServiceImpl extends BaseService implements TaskService {

  @Autowired
  private TaskMapper taskMapper;

  @Autowired
  private ProjectMapper projectMapper;

  @Autowired
  private ProjectService projectService;

  @Autowired
  private TaskMemberService taskMemberService;

  @Autowired
  private WorkflowService workflowService;

  @Autowired
  private EntryNodeService entryNodeService;

  @Autowired
  private EntryNodeMapper entryNodeMapper;

  @Autowired
  private WorkflowNodeMapper workflowNodeMapper;

  @Autowired
  private EntryHistoryService entryHistoryService;

  // ---------------- 任务 ----------------

  @Override
  public PageResult<TaskVO> tasks(TaskQueryDTO query) {
    // 判空兜底：请求体缺失时按无条件查询处理
    TaskQueryDTO cond = query == null ? new TaskQueryDTO() : query;
    long userId = currentUserId();
    Long creatorId = null;
    Long memberUserId = null;
    List<Long> teamIds = null;
    if (TaskScope.MANAGE.equalsIgnoreCase(cond.getScope())) {
      // 任务管理：谁创建的谁可见（项目管理员创建并查看自己的任务）
      creatorId = userId;
    } else if (TaskScope.RECEIVED.equalsIgnoreCase(cond.getScope())) {
      // 数据任务列表：下发给本人负责团队的任务，并入本人已被配置为任务成员的任务
      // （普通标注员不是团队负责人，但被「成员配置」加入任务后同样需要看到该任务）
      List<Long> leaderTeamIds = taskMapper.selectLeaderTeamIds(userId);
      teamIds = leaderTeamIds.isEmpty() ? null : leaderTeamIds;
      memberUserId = userId;
    } else {
      // 兼容旧调用：团队负责人（且非超管）限定本团队，其他角色可见全部
      List<Long> leaderTeamIds = taskMapper.selectLeaderTeamIds(userId);
      if (!leaderTeamIds.isEmpty() && !isSuperAdmin(userId)) {
        teamIds = leaderTeamIds;
      }
    }
    List<TaskVO> rows =
        taskMapper.selectTasks(cond.getTaskNo(), cond.getTaskName(), cond.getProjectName(),
            cond.getStatus(), cond.getTeamName(), creatorId, teamIds, memberUserId);
    fillTaskScope(rows);
    return PageResult.of(rows);
  }

  /** 是否超管（超管不受团队负责人范围限制）。 */
  private boolean isSuperAdmin(long userId) {
    try {
      return LoginHelper.isSuperAdmin(userId);
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public TaskVO task(Long id) {
    TaskVO task = taskMapper.selectTask(id);
    if (task == null) {
      throw new ServiceException("任务不存在");
    }
    fillTaskScope(List.of(task));
    return task;
  }

  /**
   * 拆分任务：按任务下发的数据批次 × 数据类型创建作业并绑定资产。
   * 多批次时每个批次、每种类型各生成一个作业，作业上记录所属批次；
   * 未绑定批次的历史任务回退为按项目整体拆分（兼容旧数据）。
   */
  @Override
  @Transactional
  public TaskSplitVO splitTask(Long id) {
    TaskVO task = task(id);
    Long projectId = taskMapper.selectTaskProjectId(id);
    List<Long> batchIds = taskMapper.selectTaskBatchIds(id);
    if (batchIds.isEmpty()) {
      Long latestBatchId = taskMapper.selectLatestBatchId(projectId);
      if (latestBatchId != null) {
        batchIds = List.of(latestBatchId);
      }
    }
    long existingJobCount = taskMapper.countJobsByTask(id);
    if (existingJobCount == 0) {
      String dataType = task.getDataType() == null ? "" : task.getDataType();
      // 混合任务展开成多个作业：文本与图片/点云并列，否则混合任务下的文本资产进不了任何作业
      List<String> jobTypes = MediaType.MIXED.equals(dataType)
          ? List.of(MediaType.IMAGE, MediaType.POINT_CLOUD, MediaType.TEXT)
          : List.of(dataType);
      if (batchIds.isEmpty()) {
        // 既无下发批次的项目也没有任何批次：按项目整体兜底
        for (String jobType : jobTypes) {
          insertJobWithScope(id, projectId, null, jobType);
        }
      } else {
        for (Long batchId : batchIds) {
          for (String jobType : jobTypes) {
            insertJobWithScope(id, projectId, batchId, jobType);
          }
        }
      }
    }
    long jobCount = taskMapper.countJobsByTask(id);
    taskMapper.updateTaskAfterSplit(id, jobCount, currentUserId());
    writeOperationLog("SPLIT_TASK", "task", id, Map.of("jobCount", jobCount));
    return new TaskSplitVO(id, jobCount);
  }

  /** 按下发范围创建作业并挂载对应资产；batchId 为空表示整个项目，无资产的作业不生成。 */
  private void insertJobWithScope(Long taskId, Long projectId, Long batchId, String jobType) {
    int assetCount = batchId == null
        ? taskMapper.countAssetsByProjectAndType(projectId, jobType)
        : taskMapper.countAssetsByBatchAndType(batchId, jobType);
    if (assetCount <= 0) {
      return;
    }
    Map<String, Object> p = new HashMap<>();
    p.put("taskId", taskId);
    p.put("projectId", projectId);
    p.put("batchId", batchId);
    p.put("jobNo", "JOB-" + taskId + "-" + nextCode("label_job"));
    p.put("dataType", jobType);
    p.put("assetCount", assetCount);
    p.put("userId", currentUserId());
    taskMapper.insertJob(p);
    long jobId = asLong(p.get("id"));
    if (batchId == null) {
      taskMapper.insertJobAssets(jobId, projectId, jobType);
    } else {
      taskMapper.insertJobAssetsByBatch(jobId, batchId, jobType);
    }
  }

  @Override
  public TaskPublishVO publishTask(Long id) {
    taskMapper.updateTaskStatus(id, LabelJobStatus.PENDING_ASSIGN, currentUserId());
    writeOperationLog("PUBLISH_TASK", "task", id, Map.of("taskId", id));
    return new TaskPublishVO(id, LabelJobStatus.PENDING_ASSIGN);
  }

  @Override
  public PageResult<AssetBatchVO> taskBatches(Long taskId, String scope) {
    return PageResult.of(taskMapper.selectTaskBatchesWithCount(taskId, teamScopeUserIds(scope),
        mineUserId(scope)));
  }

  /**
   * 解析「我的团队」条目范围：scope=myteam 时返回本人负责团队的成员用户ID（含本人，便于领取后仍可见），
   * 否则返回 null 表示不做团队范围限制（团队负责人 / 任务管理员看任务池全部数据）。
   */
  private List<Long> teamScopeUserIds(String scope) {
    if (!TaskScope.MYTEAM.equalsIgnoreCase(scope)) {
      return null;
    }
    long userId = currentUserId();
    List<Long> teamIds = taskMapper.selectLeaderTeamIds(userId);
    List<Long> userIds = teamIds.isEmpty()
        ? new ArrayList<>()
        : new ArrayList<>(taskMapper.selectTeamMemberUserIds(teamIds));
    if (!userIds.contains(userId)) {
      userIds.add(userId);
    }
    return userIds;
  }

  /** scope=mine 时返回当前用户ID（条目列表仅展示本人已领取的条目），否则返回 null。 */
  private Long mineUserId(String scope) {
    return TaskScope.MINE.equalsIgnoreCase(scope) ? currentUserId() : null;
  }

  @Override
  public PageResult<AssetVO> taskItems(Long taskId) {
    List<TaskEntryVO> rows = taskMapper.selectEntries(taskId, null, null, null, null, null, 0, 500);
    List<AssetVO> list = new ArrayList<>(rows.size());
    for (TaskEntryVO entry : rows) {
      AssetVO vo = new AssetVO();
      vo.setId(entry.getId());
      vo.setBatchId(entry.getBatchId());
      vo.setMediaType(entry.getMediaType());
      vo.setFileName(entry.getFileName());
      vo.setFileUrl(entry.getFileUrl());
      vo.setThumbnailUrl(entry.getThumbnailUrl());
      vo.setStatus(entry.getLabelStatus());
      vo.setCreateTime(entry.getCreateTime());
      list.add(vo);
    }
    return PageResult.of(list);
  }

  // ---------------- 任务数据条目（条目级分配） ----------------

  @Override
  public PageResult<TaskEntryVO> entries(TaskEntryListQueryDTO dto) {
    Long taskId = dto.getTaskId();
    if (taskId == null) {
      throw new ServiceException("任务ID不能为空");
    }
    int page = dto.getPageNum() == null || dto.getPageNum() < 1 ? 1 : dto.getPageNum();
    int size = dto.getPageSize() == null || dto.getPageSize() < 1 ? 20 : dto.getPageSize();
    String tab = dto.getTab();
    String keyword = dto.getKeyword();
    String scope = dto.getScope();
    Long batchId = dto.getBatchId();

    // 该端点只负责「按节点过滤」：nodeKey 决定走 label / review_N 等节点逻辑。
    // 「总览」语义（跨节点聚合该 task+batch 所有条目）由前端改调
    // /tasks/overview-entries 端点实现；本端点不再处理总览分支。
    String node = dto.getNodeKey() == null || dto.getNodeKey().isBlank()
        ? WorkflowService.KEY_LABEL : dto.getNodeKey().trim();
    if (WorkflowService.KEY_LABEL.equals(node)) {
      // 标注节点：保持原逻辑，避免破坏旧接口与旧数据契约
      List<Long> scopeUserIds = teamScopeUserIds(scope);
      Long mineUserId = mineUserId(scope);
      long total = taskMapper.countEntries(taskId, batchId, tab, keyword, scopeUserIds, mineUserId);
      if (total == 0) {
        return new PageResult<>(0, List.of());
      }
      List<TaskEntryVO> rows = taskMapper.selectEntries(taskId, batchId, tab, keyword, scopeUserIds,
          mineUserId, (page - 1) * size, size);
      rows.forEach(r -> fillLabelNodeFields(r));
      fillCurrentNodes(taskId, rows);
      return new PageResult<>(total, rows);
    }
    // 审核/其他节点：按 label_entry_node 状态过滤（节点字段已由 SQL 直接带出）
    ensureNodeRecords(taskId, node);
    long userId = currentUserId();
    long total = taskMapper.countEntriesByNode(taskId, batchId, tab, keyword, node, userId, scope);
    if (total == 0) {
      return new PageResult<>(0, List.of());
    }
    List<TaskEntryVO> rows = taskMapper.selectEntriesByNode(taskId, batchId, tab, keyword, node,
        userId, scope, (page - 1) * size, size);
    rows.forEach(r -> r.setNodeKey(node));
    fillCurrentNodes(taskId, rows);
    return new PageResult<>(total, rows);
  }

  @Override
  public TaskEntrySummaryVO entrySummary(Long taskId, Long batchId, String scope, String nodeKey) {
    // entrySummary 只负责按节点统计：「总览」= 节点 label 时按 selectEntrySummary 取数；
    // 节点 review_N 时按 selectEntrySummaryByNode 取数。跨节点「总览」聚合由前端调
    // /tasks/{id}/overview-entries/summary 端点实现。
    // batchId 可空，为空时跨 batch 聚合（兼容无批次视图的特殊场景）。
    String node = nodeKey == null || nodeKey.isBlank() ? WorkflowService.KEY_LABEL : nodeKey.trim();
    TaskEntrySummaryVO vo;
    if (WorkflowService.KEY_LABEL.equals(node)) {
      vo = taskMapper.selectEntrySummary(taskId, batchId, teamScopeUserIds(scope), mineUserId(scope));
    } else {
      ensureNodeRecords(taskId, node);
      vo = taskMapper.selectEntrySummaryByNode(taskId, batchId, node, scope, currentUserId());
    }
    vo = vo == null ? new TaskEntrySummaryVO() : vo;
    vo.setNodeKey(node);
    return vo;
  }

  // ---------------- 任务详情「总览」（跨节点聚合，不受 nodeKey 限制） ----------------

  /** 「总览」分页上限，避免前端传入过大 pageSize。 */
  private static final int OVERVIEW_MAX_PAGE_SIZE = 100;

  @Override
  public PageResult<TaskOverviewEntryVO> overviewEntries(TaskOverviewEntriesDTO dto) {
    Long taskId = dto.getTaskId();
    if (taskId == null) {
      throw new ServiceException("任务ID不能为空");
    }
    int page = dto.getPageNum() == null || dto.getPageNum() < 1 ? 1 : dto.getPageNum();
    int size = dto.getPageSize() == null || dto.getPageSize() < 1 ? 20 : dto.getPageSize();
    size = Math.min(size, OVERVIEW_MAX_PAGE_SIZE);
    int offset = (page - 1) * size;
    // 角色可见性分叉：团队负责人 / 任务管理员 / 超管看全部条目；普通成员仅看本人参与过的条目。
    // 两者均不依赖节点表记录是否存在；状态默认不过滤，前端按页签显式传 statusList 时才过滤。
    long userId = currentUserId();
    String scope = isTaskManager(taskId, userId) ? null : TaskScope.MINE;
    List<String> statuses = (dto.getStatusList() == null || dto.getStatusList().isEmpty())
        ? null : dto.getStatusList();
    long total = taskMapper.countOverviewEntries(taskId, dto.getBatchId(),
        statuses, dto.getKeyword(), scope, userId);
    if (total == 0) {
      return new PageResult<>(0L, List.of());
    }
    List<TaskOverviewEntryVO> rows = taskMapper.selectOverviewEntries(taskId,
        dto.getBatchId(), statuses, dto.getKeyword(), scope, userId, offset, size);
    fillOverviewNodeNames(taskId, rows);
    return new PageResult<>(total, rows);
  }

  /**
   * 填充总览行「当前节点」：口径与 fillCurrentNodes 保持一致。
   * <ul>
   *   <li>未提交评审（待领取/标注中/修订中/已跳过/已放弃，含被驳回回到修订中/未修订）→ 标注节点；</li>
   *   <li>已提交且本级已通过 → 已通过（不属于任何节点）；</li>
   *   <li>已提交且未通过（含被打回的条目）→ 当前节点为最新一条节点记录所属工作流节点
   *       （即被打回前所在的审核节点）。</li>
   * </ul>
   * 此前直接用 row.nodeKey（= latest_node_id 指向的节点）当当前节点，导致被驳回回到「修订中」的条目
   * 仍显示原审核节点；此处按 labelStatus / reviewStatus 修正，与按节点过滤列表的行为统一。
   */
  private void fillOverviewNodeNames(Long taskId, List<TaskOverviewEntryVO> rows) {
    if (rows == null || rows.isEmpty()) {
      return;
    }
    Long projectId = workflowNodeMapper.selectTaskProjectId(taskId);
    if (projectId == null) {
      return;
    }
    List<LabelWorkflowNode> nodes = workflowNodeMapper.selectNodes(projectId);
    if (nodes == null || nodes.isEmpty()) {
      return;
    }
    Map<String, LabelWorkflowNode> nodeByKey = new HashMap<>();
    for (LabelWorkflowNode node : nodes) {
      if (node.getNodeKey() != null) {
        nodeByKey.put(node.getNodeKey(), node);
      }
    }
    LabelWorkflowNode labelNode = nodeByKey.get(WorkflowService.KEY_LABEL);
    for (TaskOverviewEntryVO row : rows) {
      String labelStatus = row.getLabelStatus();
      if (!LabelEntryStatus.LABELED.equals(labelStatus)
          && !LabelEntryStatus.REVISED.equals(labelStatus)) {
        // 未提交评审（含被驳回回到修订中/未修订）：当前节点仍是标注节点
        applyCurrentNode(row, labelNode, "标注");
        continue;
      }
      if (LabelReviewStatus.PASSED.equals(row.getReviewStatus())) {
        // 本级已通过：不属于任何节点
        row.setCurrentNodeKey(null);
        row.setCurrentNodeName("已通过");
        continue;
      }
      // 已提交且未通过：当前节点 = 最新一条节点记录所属工作流节点（被打回前所在的审核节点）
      String key = row.getNodeKey();
      LabelWorkflowNode node = (key == null || key.isBlank()) ? null : nodeByKey.get(key);
      applyCurrentNode(row, node, key);
    }
  }

  @Override
  public TaskOverviewEntriesSummaryVO overviewEntriesSummary(Long taskId, Long batchId) {
    // summary 与列表同口径：负责人 / 管理员 / 超管统计全部条目，普通成员只统计本人参与过的条目
    long userId = currentUserId();
    String scope = isTaskManager(taskId, userId) ? null : TaskScope.MINE;
    TaskOverviewEntriesSummaryVO vo = taskMapper.selectOverviewEntriesSummary(taskId, batchId,
        scope, userId);
    return vo == null ? new TaskOverviewEntriesSummaryVO() : vo;
  }

  /**
   * 当前用户是否为该任务的「团队负责人 / 任务管理员 / 超管」。
   * 团队总览的权限闸门——判定后路由到对应 SQL。
   */
  private boolean isTaskManager(Long taskId, Long userId) {
    try {
      if (LoginHelper.isSuperAdmin(userId)) {
        return true;
      }
    } catch (Exception ignored) {
      // 忽略异常，按非超管继续判定
    }
    if (taskMapper.countTaskTeamLeader(taskId, userId) > 0) {
      return true;
    }
    return taskMemberService.isTaskAdmin(taskId, userId);
  }

  /**
   * 确保任务下全部条目存在指定节点的 label_entry_node 记录。
   */
  private void ensureNodeRecords(Long taskId, String nodeKey) {
    if (taskId == null || nodeKey == null || nodeKey.isBlank()) {
      return;
    }
    entryNodeService.initNodeForTask(taskId, nodeKey, WorkflowService.NODE_TYPE_REVIEW);
  }


  /**
   * 把领取/接管的条目落库 label 节点 claimed 记录，供总览 / 节点视图拼接节点流转显示字段。
   */
  private void claimLabelNodeAndSyncLatest(Long taskId, java.util.List<Long> entryIds,
      Long operatorId, Long userId) {
    if (entryIds == null || entryIds.isEmpty()) {
      return;
    }
    taskMapper.insertEntryNodeClaim(taskId, entryIds, operatorId, userId);
    // ON DUPLICATE KEY UPDATE 首次插入会产生新节点记录行，需把 entry 冗余的
    // latest_node_id 刷到该 task 下最新记录（MAX 语义），供总览主键 JOIN；
    // 二次领取（DUPLICATE 分支）不换 id，刷新幂等。
    entryNodeService.syncLatestNodeIds(entryIds, taskId);
  }

  /**
   * 标注节点：把既有 label_status / assignee_id 映射为节点状态字段。
   */
  private void fillLabelNodeFields(TaskEntryVO vo) {
    if (vo == null) {
      return;
    }
    vo.setNodeKey(WorkflowService.KEY_LABEL);
    String status = vo.getLabelStatus();
    if (LabelEntryStatus.PENDING.equals(status)) {
      vo.setNodeStatus(EntryNodeStatus.PENDING);
    } else if (LabelEntryStatus.SKIPPED.equals(status)) {
      vo.setNodeStatus(EntryNodeStatus.SKIPPED);
      vo.setNodeSkipBy(vo.getSkipBy());
    } else if (LabelEntryStatus.ABANDONED.equals(status)) {
      vo.setNodeStatus(EntryNodeStatus.ABANDONED);
      vo.setNodeAbandonBy(vo.getAbandonBy());
    } else if (LabelEntryStatus.LABELED.equals(status) || LabelEntryStatus.REVISED.equals(status)) {
      vo.setNodeStatus(EntryNodeStatus.DONE);
    } else {
      vo.setNodeStatus(EntryNodeStatus.CLAIMED);
      vo.setNodeOperatorId(vo.getAssigneeId());
      vo.setNodeOperatorName(vo.getAssigneeName());
    }
  }

  /**
   * 填充条目「当前节点」：标注/修订/待领取阶段固定在标注节点；
   * 已提交后按节点记录定位——有领取中（claimed）记录取该节点，
   * 否则取最靠前的待领取（pending）记录节点（多级审核串行推进，历史节点已置完成）；
   * 本级审核已通过显示「已通过」；已提交但尚无节点记录时兜底取任务当前节点。
   */
  private void fillCurrentNodes(Long taskId, List<TaskEntryVO> rows) {
    if (rows == null || rows.isEmpty()) {
      return;
    }
    Long projectId = workflowNodeMapper.selectTaskProjectId(taskId);
    List<LabelWorkflowNode> nodes = projectId == null
        ? List.of()
        : workflowNodeMapper.selectNodes(projectId);
    Map<String, LabelWorkflowNode> nodeByKey = new LinkedHashMap<>();
    Map<String, Integer> nodeOrder = new HashMap<>();
    for (LabelWorkflowNode node : nodes) {
      nodeByKey.put(node.getNodeKey(), node);
      nodeOrder.put(node.getNodeKey(), node.getSortOrder() == null ? 0 : node.getSortOrder());
    }
    List<Long> entryIds = rows.stream()
        .map(TaskEntryVO::getId)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
    Map<Long, List<LabelEntryNode>> recordsByEntry = new HashMap<>();
    if (!entryIds.isEmpty()) {
      for (LabelEntryNode record : entryNodeMapper.selectByEntryIds(entryIds)) {
        recordsByEntry.computeIfAbsent(record.getEntryId(), k -> new ArrayList<>()).add(record);
      }
    }
    String taskCurrentKey = workflowNodeMapper.selectTaskCurrentNode(taskId);
    for (TaskEntryVO row : rows) {
      String labelStatus = row.getLabelStatus();
      if (!LabelEntryStatus.LABELED.equals(labelStatus)
          && !LabelEntryStatus.REVISED.equals(labelStatus)) {
        // 标注/修订/待领取/已跳过/已放弃：仍在标注节点
        applyCurrentNode(row, nodeByKey.get(WorkflowService.KEY_LABEL), "标注");
        continue;
      }
      if (LabelReviewStatus.PASSED.equals(row.getReviewStatus())) {
        // 本级审核已通过：等任务推进到下一级或产出合格数据
        row.setCurrentNodeName("已通过");
        continue;
      }
      List<LabelEntryNode> records = recordsByEntry.getOrDefault(row.getId(), List.of());
      String key = records.stream()
          .filter(r -> EntryNodeStatus.CLAIMED.equals(r.getStatus()))
          .map(LabelEntryNode::getNodeKey)
          .findFirst()
          .orElseGet(() -> records.stream()
              .filter(r -> EntryNodeStatus.PENDING.equals(r.getStatus()))
              .min((a, b) -> Integer.compare(
                  nodeOrder.getOrDefault(a.getNodeKey(), 0),
                  nodeOrder.getOrDefault(b.getNodeKey(), 0)))
              .map(LabelEntryNode::getNodeKey)
              .orElse(taskCurrentKey));
      // fallbackName 用 key 兜底：节点视图下 entry 的 LabelWorkflowNode 可能因项目节点配置变更
      // 而查不到（lookup miss），此时仍应至少把 nodeKey 字符串写回 VO，避免前端展示「-」。
      applyCurrentNode(row, nodeByKey.get(key), key);
    }
  }

  private void applyCurrentNode(TaskEntryVO row, LabelWorkflowNode node, String fallbackName) {
    if (node != null) {
      row.setCurrentNodeKey(node.getNodeKey());
      row.setCurrentNodeName(node.getNodeName() != null && !node.getNodeName().isBlank()
          ? node.getNodeName() : fallbackName);
    } else if (fallbackName != null) {
      // lookup miss（节点不在项目工作流节点列表里）时，至少把 nodeKey 写回 VO，
      // 避免前端展示「-」。
      row.setCurrentNodeKey(fallbackName);
      row.setCurrentNodeName(fallbackName);
    }
  }

  /**
   * 审核节点：把 label_entry_node 记录映射为 VO 节点状态字段。
   */
  /**
   * 条目级分配：claim 领取标注（把自己设为标注员）、assign 分配给其他标注员（仅团队负责人）。
   * 分配后同步把条目归入 (任务, 批次, 类型, 标注员) 对应的作业，复用既有标注/审核工作台。
   */
  @Override
  @Transactional
  public TaskVO assignEntries(Long taskId, EntryAssignDTO dto) {
    TaskVO task = task(taskId);
    List<Long> assetIds =
        dto.getAssetIds() == null
            ? new ArrayList<>()
            : dto.getAssetIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
    if (assetIds.isEmpty()) {
      throw new ServiceException("请先勾选要分配的数据条目");
    }
    long userId = currentUserId();
    boolean claim = dto.getAssigneeId() == null || TaskAssignMode.CLAIM.equalsIgnoreCase(dto.getMode());
    Long assigneeId = claim ? userId : dto.getAssigneeId();
    if (!claim && !isSuperAdmin(userId)) {
      // 团队负责人与任务管理员同级，都可以把条目分配给其他标注员
      boolean teamLeader = taskMapper.countTaskTeamLeader(taskId, userId) > 0;
      if (!teamLeader && !taskMemberService.isTaskAdmin(taskId, userId)) {
        throw new ServiceException("仅团队负责人或任务管理员可以将条目分配给其他标注员");
      }
      if (teamLeader) {
        // 团队负责人只能把条目分配给自己负责团队的成员
        List<Long> leaderTeamIds = taskMapper.selectLeaderTeamIds(userId);
        if (!leaderTeamIds.isEmpty()) {
          List<Long> members = taskMapper.selectTeamMemberUserIds(leaderTeamIds);
          if (!members.isEmpty() && !members.contains(assigneeId)) {
            throw new ServiceException("只能把条目分配给本团队的标注员");
          }
        }
      } else {
        // 任务管理员只能把条目分配给该任务下发团队的成员
        List<Long> members = taskMemberService.taskTeamMemberUserIds(taskId);
        if (!members.isEmpty() && !members.contains(assigneeId)) {
          throw new ServiceException("只能把条目分配给该任务下发团队的成员");
        }
      }
    }
    Long reviewerId = dto.getReviewerId() != null
        ? dto.getReviewerId()
        : findUserId(dto.getReviewerName(), null);
    if (claim) {
      // 「领取标注」可领取待办项中的三类条目：
      // 待分配（pending 且无人领取）→ 领取为「标注中」；
      // 未修订（审核/复审驳回后打回）→ 领取为「修订中」，作业回到标注中供工作台继续修订；
      // 修订中（含他人名下）→ 接管为「修订中」，作业归入领取人名下继续修订
      List<Long> pendingIds = new ArrayList<>();
      List<Long> reviseIds = new ArrayList<>();
      boolean hasNewWork = false;
      for (Map<String, Object> row : taskMapper.selectEntryStatuses(assetIds)) {
        String status = stringValue(mapValue(row, "labelStatus"));
        Long entryId = asLong(mapValue(row, "entryId"));
        Long entryAssignee = asLong(mapValue(row, "assigneeId"));
        // 批次守卫：传入 batchId 时（前端领取时带当前选中批次），不允许跨批次混领
        Long rowBatchId = asLong(mapValue(row, "batchId"));
        if (dto.getBatchId() != null && rowBatchId != null && !rowBatchId.equals(dto.getBatchId())) {
          throw new ServiceException("条目[" + entryId + "]不属于当前选中批次，批次与批次之间不能混领");
        }
        if (LabelEntryStatus.PENDING.equals(status)) {
          pendingIds.add(entryId);
        } else if (LabelEntryStatus.UNREVISED.equals(status)
            || LabelEntryStatus.REVISING.equals(status)) {
          reviseIds.add(entryId);
          if (entryAssignee == null || entryAssignee.longValue() != userId) {
            // 接管他人/无主的待修订条目，视同领取新条目
            hasNewWork = true;
          }
        } else {
          throw new ServiceException("仅「待办项」中待分配、未修订或修订中的条目可以领取");
        }
      }
      // 领取新条目（待分配）或接管他人修订时才需要成员资格与当前节点校验；
      // 选中条目全部是自己名下的未修订/修订中时，视为进入自己的待办，不受节点流转限制
      if (!pendingIds.isEmpty() || hasNewWork) {
        taskMemberService.assertClaimable(taskId, userId, workflowService.effectiveNodeKey(taskId));
      }
      if (!pendingIds.isEmpty()) {
        // 待分配条目只允许「无人领取」时抢占：已被领取的条目不能再被重复领取
        int claimed = taskMapper.updateEntryClaim(pendingIds, assigneeId, reviewerId, userId);
        if (claimed < pendingIds.size()) {
          throw new ServiceException(
              "所选条目中有 " + (pendingIds.size() - claimed) + " 条已被领取，不能重复领取");
        }
        // 抢占成功后同步落库 label 节点的 claimed 记录，供总览 / 节点视图拼接节点流转显示字段
        claimLabelNodeAndSyncLatest(taskId, pendingIds, assigneeId, userId);
      }
      if (!reviseIds.isEmpty()) {
        // 未修订/修订中的条目统一接管为「修订中」，作业回到标注中供工作台继续修订
        taskMapper.updateEntryTakeRevise(reviseIds, userId);
        // 接管成功后同步落库 label 节点的 claimed 记录，供总览 / 节点视图拼接节点流转显示字段
        claimLabelNodeAndSyncLatest(taskId, reviseIds, userId, userId);
        taskMapper.updateJobStatusByAssetIds(reviseIds, LabelJobStatus.LABELING, userId);
      }
      // 条目操作历史：领取标注 / 领取修订
      if (!pendingIds.isEmpty()) {
        entryHistoryService.recordBatch(pendingIds, LabelEntryHistoryAction.CLAIM, "条目领取", "标注", null);
      }
      if (!reviseIds.isEmpty()) {
        entryHistoryService.recordBatch(reviseIds, LabelEntryHistoryAction.CLAIM_REVISE, "条目领取修订", "标注", null);
      }
    } else {
      // 分配给指定标注员：先按 updateEntryAssign 的口径（未放弃的条目）挑出真正会落库的条目，
      // 再同步 label 节点的 claimed 记录。
      // 否则节点视图 / 待办项（走 label_entry_node 口径）仍把这些条目当「未领取」，
      // 与「标注员」列（label_data_entry.assignee_id）显示不一致。
      List<Long> assignableIds = new ArrayList<>();
      for (Map<String, Object> row : taskMapper.selectEntryStatuses(assetIds)) {
        String status = stringValue(mapValue(row, "labelStatus"));
        Long entryId = asLong(mapValue(row, "entryId"));
        if (entryId != null && !LabelEntryStatus.ABANDONED.equals(status)) {
          assignableIds.add(entryId);
        }
      }
      taskMapper.updateEntryAssign(assetIds, assigneeId, reviewerId, LabelEntryStatus.LABELING, userId);
      claimLabelNodeAndSyncLatest(taskId, assignableIds, assigneeId, userId);
      // 条目操作历史：分配
      String assigneeName = dto.getAssigneeName();
      String detail = assigneeName != null && !assigneeName.isBlank()
          ? "分配给 " + assigneeName
          : (assigneeId != null ? "分配给用户#" + assigneeId : null);
      entryHistoryService.recordBatch(assetIds, LabelEntryHistoryAction.ASSIGN, "条目分配", "任务管理员", detail);
    }
    // 条目（数据集合包）流转状态同步到包内数据资产，工作台/审核按数据资产继续作业
    taskMapper.syncAssetsFromEntries(assetIds);
    syncEntryJobs(taskId, assetIds, assigneeId, reviewerId, userId);
    writeOperationLog(claim ? "CLAIM_ENTRIES" : "ASSIGN_ENTRIES", "task", taskId,
        Map.of("count", assetIds.size(), "assigneeId", assigneeId));
    if (!claim) {
      createNotification(assigneeId, "新的标注条目",
          "任务「" + task.getTaskName() + "」新增 " + assetIds.size() + " 条待标注数据", "task", taskId);
    }
    syncTaskStatus(taskId);
    return task(taskId);
  }

  /**
   * 自动领取标注（「领取标注」未勾选条目时）：
   * 本人有待办时返回第一条待办并直接进入——被驳回的「未修订」条目最优先，领取即置为「修订中」；
   * 其次是进行中的「标注中/修订中」条目；无待办时在任务池内随机领取一条「待分配且无人领取」的条目；
   * 也没有时接管一条被驳回待修订的条目（未修订优先于修订中，含他人名下），统一进入修订中修订。
   * 返回条目与对应作业ID，前端据此直接进入标注工作台。
   */
  @Override
  @Transactional
  public TaskClaimAutoVO claimAutoEntry(Long taskId, Long batchId) {
    task(taskId);
    long userId = currentUserId();
    // 有自己名下待办（标注中/修订中/未修订，含被驳回修订）时不做节点校验，点击「领取标注」直接进入；
    // 传入 batchId 时只在本批次内定位待办，批次与批次之间不混领
    TaskEntryVO todo = taskMapper.selectMyTodoEntry(taskId, userId, batchId);
    if (todo != null) {
      String source = "todo";
      if (LabelEntryStatus.UNREVISED.equals(todo.getLabelStatus())) {
        // 被驳回的待办：领取即进入「修订中」，并把作业置回「标注中」供工作台继续修订
        List<Long> entryIds = List.of(todo.getId());
        taskMapper.updateEntryClaimRevise(entryIds, userId);
        // 领取修订成功后同步落库 label 节点的 claimed 记录，供总览 / 节点视图拼接节点流转显示字段
        claimLabelNodeAndSyncLatest(taskId, entryIds, userId, userId);
        taskMapper.syncAssetsFromEntries(entryIds);
        taskMapper.updateJobStatusByAssetIds(entryIds, LabelJobStatus.LABELING, userId);
        syncEntryJobs(taskId, entryIds, userId, null, userId);
        syncTaskStatus(taskId);
        entryHistoryService.recordBatch(entryIds, LabelEntryHistoryAction.CLAIM_REVISE, "条目领取修订", "标注", "自动领取待办");
        source = "revise";
      }
      Long jobId = taskMapper.selectJobIdByAssetForTask(taskId, todo.getId());
      writeOperationLog("CLAIM_AUTO", "task", taskId,
          Map.of("source", source, "entryId", todo.getId()));
      return new TaskClaimAutoVO(todo.getId(), todo.getEntryName(), todo.getMediaType(), jobId,
          source);
    }
    // 没有自己的待办：领取新条目（待分配）或接管修订前，校验必须是任务成员且归属任务当前有效节点
    taskMemberService.assertClaimable(taskId, userId, workflowService.effectiveNodeKey(taskId));
    TaskEntryVO pending = taskMapper.selectRandomPendingEntry(taskId, batchId);
    if (pending == null) {
      // 任务池内已无「待分配」条目：接管一条被驳回待修订的条目（未修订优先于修订中，含他人名下）
      TaskEntryVO revisable = taskMapper.selectRandomRevisableEntry(taskId, batchId);
      if (revisable == null) {
        throw new ServiceException("任务池中已无可领取的条目（待分配/未修订/修订中），且你没有待办数据");
      }
      List<Long> entryIds = List.of(revisable.getId());
      int taken = taskMapper.updateEntryTakeRevise(entryIds, userId);
      if (taken <= 0) {
        throw new ServiceException("该条目刚被其他成员处理，请重新点击「领取标注」");
      }
      // 接管修订成功后同步落库 label 节点的 claimed 记录，供总览 / 节点视图拼接节点流转显示字段
      claimLabelNodeAndSyncLatest(taskId, entryIds, userId, userId);
      taskMapper.syncAssetsFromEntries(entryIds);
      // 作业置回「标注中」并归入接管人名下，供工作台继续修订
      taskMapper.updateJobStatusByAssetIds(entryIds, LabelJobStatus.LABELING, userId);
      syncEntryJobs(taskId, entryIds, userId, null, userId);
      syncTaskStatus(taskId);
      entryHistoryService.recordBatch(entryIds, LabelEntryHistoryAction.CLAIM_REVISE, "条目领取修订", "标注", "接管待修订条目");
      Long jobId = taskMapper.selectJobIdByAssetForTask(taskId, revisable.getId());
      writeOperationLog("CLAIM_AUTO", "task", taskId,
          Map.of("source", "take_revise", "entryId", revisable.getId()));
      return new TaskClaimAutoVO(revisable.getId(), revisable.getEntryName(),
          revisable.getMediaType(), jobId, "revise");
    }
    List<Long> entryIds = List.of(pending.getId());
    int claimed = taskMapper.updateEntryClaim(entryIds, userId, null, userId);
    if (claimed <= 0) {
      throw new ServiceException("该条目刚被其他成员领取，请重新点击「领取标注」");
    }
    // 随机领取成功后同步落库 label 节点的 claimed 记录，供总览 / 节点视图拼接节点流转显示字段
    claimLabelNodeAndSyncLatest(taskId, entryIds, userId, userId);
    taskMapper.syncAssetsFromEntries(entryIds);
    syncEntryJobs(taskId, entryIds, userId, null, userId);
    writeOperationLog("CLAIM_AUTO", "task", taskId,
        Map.of("source", "random", "entryId", pending.getId()));
    syncTaskStatus(taskId);
    entryHistoryService.recordBatch(entryIds, LabelEntryHistoryAction.CLAIM, "条目领取", "标注", "自动领取");
    Long jobId = taskMapper.selectJobIdByAssetForTask(taskId, pending.getId());
    return new TaskClaimAutoVO(pending.getId(), pending.getEntryName(), pending.getMediaType(),
        jobId, "random");
  }

  /**
   * 批量查询条目归属作业ID（任务详情「查看」多选/全选时按作业分组打开只读工作台）。
   * 尚未领取（无作业承载）的条目不在返回结果中。
   */
  @Override
  public List<EntryJobVO> entryJobIds(Long taskId, List<Long> entryIds) {
    List<Long> distinctIds = entryIds == null ? List.of()
        : entryIds.stream().filter(java.util.Objects::nonNull).distinct().limit(500).toList();
    if (distinctIds.isEmpty()) {
      return List.of();
    }
    return taskMapper.selectEntryJobIds(taskId, distinctIds);
  }

  /**
   * 标注员跳过：把「标注中/修订中」的选中条目置为「已跳过」，等待管理员重新分配。
   * 仅能跳过自己名下的条目（超管不受限）。
   */
  @Override
  @Transactional
  public TaskVO skipEntries(Long taskId, EntryAssignDTO dto) {
    task(taskId);
    List<Long> assetIds = distinctEntryIds(dto);
    assertTaskEntries(taskId, assetIds);
    long userId = currentUserId();
    boolean superAdmin = isSuperAdmin(userId);
    for (Map<String, Object> row : taskMapper.selectEntryStatuses(assetIds)) {
      String status = stringValue(mapValue(row, "labelStatus"));
      if (!LabelEntryStatus.LABELING.equals(status) && !LabelEntryStatus.REVISING.equals(status)) {
        throw new ServiceException("仅「标注中/修订中」的条目可以跳过");
      }
      Long assigneeId = asLong(mapValue(row, "assigneeId"));
      if (!superAdmin && assigneeId != null && !assigneeId.equals(userId)) {
        throw new ServiceException("只能跳过自己名下的条目");
      }
    }
    taskMapper.updateEntrySkip(assetIds, userId, superAdmin);
    taskMapper.syncAssetsFromEntries(assetIds);
    entryHistoryService.recordBatch(assetIds, LabelEntryHistoryAction.SKIP, "条目跳过", "标注", null);
    writeOperationLog("SKIP_ENTRIES", "task", taskId, Map.of("count", assetIds.size()));
    syncTaskStatus(taskId);
    return task(taskId);
  }

  /**
   * 领取修订：把「未修订」的选中条目录取为自己修订，进入「修订中」。
   * 修订提交后条目变为「已修订」，等待审核人领取复审。
   */
  @Override
  @Transactional
  public TaskVO claimReviseEntries(Long taskId, EntryAssignDTO dto) {
    task(taskId);
    List<Long> assetIds = distinctEntryIds(dto);
    assertTaskEntries(taskId, assetIds);
    for (Map<String, Object> row : taskMapper.selectEntryStatuses(assetIds)) {
      if (!LabelEntryStatus.UNREVISED.equals(stringValue(mapValue(row, "labelStatus")))) {
        throw new ServiceException("仅「未修订」的条目可以领取修订");
      }
    }
    long userId = currentUserId();
    // 修订同属标注节点：成员归属节点需与任务当前有效节点一致（驳回时有效节点即为标注节点）
    taskMemberService.assertClaimable(taskId, userId, workflowService.effectiveNodeKey(taskId));
    taskMapper.updateEntryClaimRevise(assetIds, userId);
    // 批量领取修订成功后同步落库 label 节点的 claimed 记录，供总览 / 节点视图拼接节点流转显示字段
    claimLabelNodeAndSyncLatest(taskId, assetIds, userId, userId);
    taskMapper.syncAssetsFromEntries(assetIds);
    // 作业回到「标注中」，标注员可在标注工作台继续修订
    taskMapper.updateJobStatusByAssetIds(assetIds, LabelJobStatus.LABELING, userId);
    syncEntryJobs(taskId, assetIds, userId, null, userId);
    entryHistoryService.recordBatch(assetIds, LabelEntryHistoryAction.CLAIM_REVISE, "条目领取修订", "标注", null);
    writeOperationLog("CLAIM_REVISE_ENTRIES", "task", taskId,
        Map.of("count", assetIds.size(), "assigneeId", userId));
    syncTaskStatus(taskId);
    return task(taskId);
  }

  /** 审核驳回后把相关作业退回标注（作业状态置为已退回），便于标注员重新修订。 */
  @Override
  @Transactional
  public void markEntriesReturned(List<Long> assetIds) {
    if (assetIds == null || assetIds.isEmpty()) {
      return;
    }
    taskMapper.updateJobStatusByAssetIds(assetIds, LabelJobStatus.REVIEW_REJECTED, currentUserId());
  }

  /**
   * 标注员放弃条目：把「标注中/修订中」且在自己名下的选中条目置为「已放弃」，
   * 并把数据从作业中摘除（工作台不再显示已放弃的数据）。
   *
   * <p>已放弃条目对团队全体成员可见（便于了解进度），但不能被其他成员领取，
   * 只有放弃人本人可以恢复。</p>
   */
  @Override
  @Transactional
  public TaskVO abandonEntries(Long taskId, EntryAssignDTO dto) {
    task(taskId);
    List<Long> assetIds = distinctEntryIds(dto);
    assertTaskEntries(taskId, assetIds);
    long userId = currentUserId();
    boolean superAdmin = isSuperAdmin(userId);
    for (Map<String, Object> row : taskMapper.selectEntryStatuses(assetIds)) {
      String status = stringValue(mapValue(row, "labelStatus"));
      if (LabelEntryStatus.ABANDONED.equals(status)) {
        throw new ServiceException("所选条目中存在已放弃的条目，请刷新后重试");
      }
      if (!LabelEntryStatus.LABELING.equals(status) && !LabelEntryStatus.REVISING.equals(status)) {
        throw new ServiceException("仅「标注中/修订中」的条目可以放弃");
      }/*  */
      Long assigneeId = asLong(mapValue(row, "assigneeId"));
      if (!superAdmin && assigneeId != null && !assigneeId.equals(userId)) {
        throw new ServiceException("只能放弃自己名下的条目");
      }
    }
    int count = taskMapper.updateEntryAbandon(assetIds, userId, superAdmin);
    if (count <= 0) {
      throw new ServiceException("没有可放弃的条目，请刷新后重试");
    }
    // 条目流转状态同步到包内数据资产
    taskMapper.syncAssetsFromEntries(assetIds);
    // 把已放弃的数据从作业中摘除，并按剩余数据刷新作业范围（空作业自动清理）
    List<Long> jobIds = taskMapper.selectJobIdsByTaskAndEntries(taskId, assetIds);
    taskMapper.deleteJobAssetsByTaskAndAssets(taskId, assetIds);
    for (Long jobId : jobIds) {
      taskMapper.refreshJobScopeMeta(jobId, userId);
    }
    taskMapper.softDeleteEmptyJobsByTask(taskId, userId);
    entryHistoryService.recordBatch(assetIds, LabelEntryHistoryAction.ABANDON, "条目放弃", "标注", null);
    writeOperationLog("ABANDON_ENTRIES", "task", taskId,
        Map.of("count", count, "abandonBy", userId));
    syncTaskStatus(taskId);
    return task(taskId);
  }

  /**
   * 恢复条目：支持同时勾选「已放弃」与「已跳过」条目，按状态分桶走各自恢复路径。
   *
   * <ul>
   *   <li>已放弃：仅放弃人本人可恢复，标注状态回滚到放弃前状态，归属回到放弃人；</li>
   *   <li>已跳过：跳过人本人 / 项目团队负责人 / 任务管理员可恢复，标注状态回滚到跳过前状态，
   *       归属保留为跳过人（缺省归到当前恢复人）以便继续标注；按 skip_by 分桶归集作业。</li>
   * </ul>
   *
   * <p>恢复后数据重新归入对应标注员作业，作业状态回到「标注中」。</p>
   */
  @Override
  @Transactional
  public TaskVO restoreEntries(Long taskId, EntryAssignDTO dto) {
    task(taskId);
    List<Long> assetIds = distinctEntryIds(dto);
    assertTaskEntries(taskId, assetIds);
    long userId = currentUserId();
    boolean leader = taskMapper.countTaskTeamLeader(taskId, userId) > 0;
    boolean taskAdmin = taskMemberService.isTaskAdmin(taskId, userId);
    List<Long> abandonedIds = new ArrayList<>();
    // 已跳过条目按归属分桶：归属保留为跳过人本人（skip_by），缺省归到当前恢复人名下
    Map<Long, List<Long>> skippedByAssignee = new LinkedHashMap<>();
    for (Map<String, Object> row : taskMapper.selectEntryStatuses(assetIds)) {
      String status = stringValue(mapValue(row, "labelStatus"));
      Long entryId = asLong(mapValue(row, "entryId"));
      if (LabelEntryStatus.ABANDONED.equals(status)) {
        Long abandonBy = asLong(mapValue(row, "abandonBy"));
        if (abandonBy == null || !abandonBy.equals(userId)) {
          throw new ServiceException("只有放弃该条目的成员可以恢复");
        }
        abandonedIds.add(entryId);
      } else if (LabelEntryStatus.SKIPPED.equals(status)) {
        Long skipBy = asLong(mapValue(row, "skipBy"));
        boolean selfSkip = skipBy != null && skipBy.equals(userId);
        if (!selfSkip && !leader && !taskAdmin) {
          throw new ServiceException("只有跳过人本人、团队负责人或任务管理员可以恢复已跳过条目");
        }
        Long assignee = skipBy != null ? skipBy : userId;
        skippedByAssignee.computeIfAbsent(assignee, k -> new ArrayList<>()).add(entryId);
      } else {
        throw new ServiceException("仅「已放弃」或「已跳过」的条目可以恢复");
      }
    }
    int abandonedCount = 0;
    if (!abandonedIds.isEmpty()) {
      abandonedCount = taskMapper.updateEntryRestore(abandonedIds, userId);
      if (abandonedCount <= 0) {
        throw new ServiceException("没有可恢复的已放弃条目，请刷新后重试");
      }
    }
    int skippedCount = 0;
    for (Map.Entry<Long, List<Long>> entry : skippedByAssignee.entrySet()) {
      Long assignee = entry.getKey();
      List<Long> ids = entry.getValue();
      int count = taskMapper.updateEntryRestoreSkipped(ids, userId);
      if (count <= 0) {
        throw new ServiceException("没有可恢复的已跳过条目，请刷新后重试");
      }
      skippedCount += count;
      // 数据归入跳过人原作业，作业状态回到「标注中」
      syncEntryJobs(taskId, ids, assignee, null, userId);
      taskMapper.updateJobStatusByAssetIds(ids, LabelJobStatus.LABELING, userId);
    }
    if (abandonedCount + skippedCount <= 0) {
      throw new ServiceException("没有可恢复的条目，请刷新后重试");
    }
    taskMapper.syncAssetsFromEntries(assetIds);
    if (!abandonedIds.isEmpty()) {
      // 已放弃数据归入放弃人（userId 本人）作业，作业状态回到「标注中」
      syncEntryJobs(taskId, abandonedIds, userId, null, userId);
      taskMapper.updateJobStatusByAssetIds(abandonedIds, LabelJobStatus.LABELING, userId);
    }
    entryHistoryService.recordBatch(assetIds, LabelEntryHistoryAction.RESTORE, "条目恢复", "标注", null);
    writeOperationLog("RESTORE_ENTRIES", "task", taskId,
        Map.of("abandoned", abandonedCount, "skipped", skippedCount));
    syncTaskStatus(taskId);
    return task(taskId);
  }

  /**
   * 释放领取：把已领取、进行中的条目退回池子（未领取），仅团队负责人/任务管理员/超管可操作。
   *
   * <p>状态按条目自身状态自动回退：标注中→待分配（未标注）、修订中→未修订、
   * 审核中/复审中→未审核；同时清空对应处理人，并把节点记录（claimed）重置回池子（pending），
   * 供其他成员重新领取。</p>
   */
  @Override
  @Transactional
  public TaskVO releaseEntries(Long taskId, EntryAssignDTO dto) {
    task(taskId);
    List<Long> assetIds = distinctEntryIds(dto);
    assertTaskEntries(taskId, assetIds);
    long userId = currentUserId();
    if (!isSuperAdmin(userId)) {
      boolean leader = taskMapper.countTaskTeamLeader(taskId, userId) > 0;
      if (!leader && !taskMemberService.isTaskAdmin(taskId, userId)) {
        throw new ServiceException("仅团队负责人或任务管理员可以释放条目");
      }
    }
    int count = taskMapper.updateEntryRelease(assetIds, userId);
    if (count <= 0) {
      throw new ServiceException("所选条目均未被领取，无需释放");
    }
    // 被释放条目的节点记录回池子（claimed→pending），供其他成员重新领取
    for (Long entryId : assetIds) {
      entryNodeService.resetPendingClaimed(entryId);
    }
    taskMapper.syncAssetsFromEntries(assetIds);
    entryHistoryService.recordBatch(assetIds, LabelEntryHistoryAction.RELEASE, "条目释放", "任务管理员", null);
    writeOperationLog("RELEASE_ENTRIES", "task", taskId, Map.of("count", count));
    syncTaskStatus(taskId);
    return task(taskId);
  }

  /** 归一化并校验条目ID集合（DTO 字段沿用 assetIds，语义为数据条目ID）。 */
  private List<Long> distinctEntryIds(EntryAssignDTO dto) {
    List<Long> entryIds = dto.getAssetIds() == null
        ? new ArrayList<>()
        : dto.getAssetIds().stream()
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toCollection(ArrayList::new));
    if (entryIds.isEmpty()) {
      throw new ServiceException("请先勾选要操作的数据条目");
    }
    return entryIds;
  }

  /** 校验选中的条目均属于该任务。 */
  private void assertTaskEntries(Long taskId, List<Long> entryIds) {
    for (Map<String, Object> row : taskMapper.selectEntryStatuses(entryIds)) {
      Long ownerTaskId = asLong(mapValue(row, "taskId"));
      if (ownerTaskId != null && !ownerTaskId.equals(taskId)) {
        throw new ServiceException("存在不属于该任务的条目，操作已终止");
      }
    }
  }

  @Override
  public Long entryJobId(Long taskId, Long assetId) {
    return taskMapper.selectJobIdByAssetForTask(taskId, assetId);
  }

  @Override
  public List<String> myLeaderTeams() {
    return taskMapper.selectLeaderTeamNames(currentUserId());
  }

  @Override
  public List<Long> myLeaderTeamIds() {
    return taskMapper.selectLeaderTeamIds(currentUserId());
  }

  /** 把条目归入 (任务, 批次, 数据类型, 标注员) 对应的作业：不存在的作业自动创建。 */
  private void syncEntryJobs(Long taskId, List<Long> assetIds, Long assigneeId, Long reviewerId,
      long userId) {
    List<Map<String, Object>> scope = taskMapper.selectAssetScope(assetIds);
    if (scope == null || scope.isEmpty()) {
      return;
    }
    // 任务数据类型：点云/图片任务只归入对应类型资产（同包内另一类型仅作参考/附属，不进入标注作业），
    // 混合任务按资产自身类型分作业
    String taskDataType = taskMapper.selectTaskDataType(taskId);
    // 先从该任务下所有作业摘除，避免一条数据同时挂多个作业
    taskMapper.deleteJobAssetsByTaskAndAssets(taskId, assetIds);
    Long projectId = taskMapper.selectTaskProjectId(taskId);
    Map<String, Long> jobByScope = new HashMap<>();
    Map<Long, List<Long>> assetsByJob = new LinkedHashMap<>();
    for (Map<String, Object> row : scope) {
      Long assetId = asLong(mapValue(row, "assetId"));
      Long batchId = asLong(mapValue(row, "batchId"));
      String assetMediaType = stringValue(mapValue(row, "assetMediaType"));
      String dataType = resolveJobType(taskDataType, assetMediaType);
      if (dataType == null) {
        // 与任务类型不符的资产（如点云任务中的相机图）不进入标注作业，仅作参考图
        continue;
      }
      String key = batchId + "|" + dataType;
      Long jobId = jobByScope.get(key);
      if (jobId == null) {
        jobId = taskMapper.selectJobIdByScope(taskId, batchId, dataType, assigneeId);
        if (jobId == null) {
          Map<String, Object> p = new HashMap<>();
          p.put("taskId", taskId);
          p.put("projectId", projectId);
          p.put("batchId", batchId);
          p.put("jobNo", "JOB-" + taskId + "-" + nextCode("label_job"));
          p.put("dataType", dataType);
          p.put("assetCount", 0);
          p.put("userId", userId);
          taskMapper.insertJob(p);
          jobId = asLong(p.get("id"));
          taskMapper.updateJobAssignee(jobId, assigneeId, reviewerId, userId);
        }
        jobByScope.put(key, jobId);
      }
      assetsByJob.computeIfAbsent(jobId, k -> new ArrayList<>()).add(assetId);
    }
    for (Map.Entry<Long, List<Long>> entry : assetsByJob.entrySet()) {
      taskMapper.insertJobAssetsByAssetIds(entry.getKey(), entry.getValue());
      taskMapper.refreshJobScopeMeta(entry.getKey(), userId);
    }
    taskMapper.softDeleteEmptyJobsByTask(taskId, userId);
  }

  /**
   * 解析某资产应归属的作业类型（data_type）。
   * <p>点云/图片等单一类型任务：只归入同类型资产，另一类型返回 null（跳过）——
   * 例如 nuScenes 点云任务中，同包的相机图不进入标注作业，仅作为点云的参考图；
   * 混合任务：按资产自身类型分作业。</p>
   */
  private String resolveJobType(String taskDataType, String assetMediaType) {
    // 文本任务与图片/点云任务同一套口径：只归入同类型资产，另一类型不进作业
    if (MediaType.POINT_CLOUD.equals(taskDataType) || MediaType.IMAGE.equals(taskDataType)
        || MediaType.TEXT.equals(taskDataType)) {
      return taskDataType.equals(assetMediaType) ? taskDataType : null;
    }
    // 混合任务：按资产自身类型分作业，文本同样需要展开，否则文本资产会被整批丢弃、
    // 作业里挂不到任何数据，标注页也就查不到条目
    return MediaType.IMAGE.equals(assetMediaType) || MediaType.POINT_CLOUD.equals(assetMediaType)
        || MediaType.TEXT.equals(assetMediaType) ? assetMediaType : null;
  }

  /** Map 结果取列值（兼容不同 MyBatis 配置下列名大小写差异）。 */
  private Object mapValue(Map<String, Object> row, String key) {
    if (row.containsKey(key)) {
      return row.get(key);
    }
    for (Map.Entry<String, Object> entry : row.entrySet()) {
      if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
        return entry.getValue();
      }
    }
    return null;
  }

  @Override
  public TaskBatchAssignVO assignBatch(Long taskId, Long batchId) {
    taskMapper.updateTaskStatus(taskId, LabelJobStatus.PENDING_ASSIGN, currentUserId());
    writeOperationLog("ASSIGN_BATCH", "task", taskId, Map.of("batchId", batchId));
    return new TaskBatchAssignVO(taskId, batchId);
  }

  @Override
  public List<AssetBatchVO> appendableBatches(Long taskId) {
    TaskVO task = task(taskId);
    if (task == null || task.getProjectId() == null) {
      return new ArrayList<>();
    }
    return taskMapper.selectAppendableBatches(task.getProjectId(), taskId);
  }

  /**
   * 追加批次：把新批次并入已有标注任务，使其继续标注。
   * <p>沿用 createTask（syncTaskBatches 增量新增分支）+ splitTask（insertJobWithScope）的批次处理路径：
   * 1) 增量写入 label_task_batch；2) 批次内数据条目归属到任务（bindEntriesToTask）；
   * 3) 按任务数据类型为新批次展开作业并挂载资产（insertJobWithScope）；4) 累加资产总数并置为 pending_assign 继续可领取。</p>
   */
  @Override
  @Transactional
  public TaskVO appendBatches(Long taskId, TaskSaveDTO dto) {
    List<Long> batchIds = dto.getBatchIds();
    if (batchIds == null || batchIds.isEmpty()) {
      throw new ServiceException("请至少选择一个要追加的批次");
    }
    TaskVO task = task(taskId);
    if (task == null) {
      throw new ServiceException("任务不存在");
    }
    Long projectId = task.getProjectId();
    if (projectId == null) {
      throw new ServiceException("任务缺少项目信息，无法追加批次");
    }
    Long userId = currentUserId();
    // 去重并排除已在本任务的批次
    Set<Long> existing = new HashSet<>(taskMapper.selectTaskBatchIds(taskId));
    List<Long> toAdd = batchIds.stream()
        .filter(Objects::nonNull)
        .filter(b -> !existing.contains(b))
        .distinct()
        .collect(Collectors.toList());
    if (toAdd.isEmpty()) {
      throw new ServiceException("所选批次均已在该任务中，无需追加");
    }
    // 同一批次只能下发到一个任务（排除自身）
    assertBatchesNotOccupied(taskId, toAdd);
    // 增量写入任务-批次关联
    taskMapper.insertTaskBatches(taskId, toAdd, userId);
    // 批次内数据条目归属到任务
    taskMapper.bindEntriesToTask(taskId, toAdd, userId);
    // 为新批次按数据类型展开作业并挂载资产，任务即可继续标注
    String dataType = task.getDataType() == null ? "" : task.getDataType();
    List<String> jobTypes = MediaType.MIXED.equals(dataType)
        ? List.of(MediaType.IMAGE, MediaType.POINT_CLOUD, MediaType.TEXT)
        : List.of(dataType);
    for (Long batchId : toAdd) {
      for (String jobType : jobTypes) {
        insertJobWithScope(taskId, projectId, batchId, jobType);
      }
    }
    // 更新资产总数与状态（pending_assign：新批次数据进入可领取池）
    int addedAssets = countAssetsOfBatches(toAdd);
    int newAssetCount = (task.getAssetCount() == null ? 0 : task.getAssetCount()) + addedAssets;
    taskMapper.updateTaskAssetCount(taskId, newAssetCount, userId);
    taskMapper.updateTaskAfterSplit(taskId, taskMapper.countJobsByTask(taskId), userId);
    writeOperationLog("APPEND_BATCH", "task", taskId, Map.of("addedBatchIds", toAdd));
    return task(taskId);
  }

  /**
   * 创建任务。下发范围 = 项目下选中的数据批次集合，承接团队 = 项目团队池中选中的团队集合；
   * 兼容旧前端（仅传 projectName/teamName，未传批次时任务不限定批次、拆分回退为整个项目）。
   */
  @Override
  @Transactional
  public TaskVO createTask(TaskSaveDTO dto) {
    Long projectId = resolveTaskProjectId(dto);
    // 仅新建任务时校验：只有已启动（进行中）的项目才允许下发任务
    assertProjectStarted(projectId);
    Long classSetId = firstClassSetId(projectId);
    List<Long> teamIds = resolveTaskTeamIds(dto);
    Long teamId = teamIds.isEmpty() ? null : teamIds.get(0);
    List<Long> batchIds = resolveTaskBatchIds(projectId, dto.getBatchIds());
    assertBatchesNotOccupied(null, batchIds);
    String dataType = dto.getDataType() == null ? MediaType.IMAGE : dto.getDataType();
    int assetCount = dto.getAssetCount() != null ? dto.getAssetCount() : countAssetsOfBatches(batchIds);
    String taskNo = "TASK-" + LocalDateTime.now().getYear() + "-" + nextCode("label_task");
    LabelTask task = new LabelTask();
    task.setProjectId(projectId);
    task.setClassSetId(classSetId);
    task.setTaskNo(taskNo);
    task.setTaskName(dto.getTaskName());
    task.setDataType(dataType);
    task.setCreatorId(currentUserId());
    task.setTeamId(teamId);
    task.setAssetCount(assetCount);
    // 预标注引入开关：默认关闭；开启后标注工作台把预标注框注入画布（可删改、首次保存转正）
    task.setPreAnnotationEnabled(normalizePreAnnotationEnabled(dto.getPreAnnotationEnabled()));
    task.setUserId(currentUserId());
    taskMapper.insertTask(task);
    long newId = asLong(task.getId());
    syncTaskTeams(newId, teamIds);
    syncTaskBatches(newId, batchIds);
    writeOperationLog("CREATE_TASK", "task", newId, Map.of("taskNo", taskNo));
    return task(newId);
  }

  /**
   * 更新任务：按 dto.id 更新基本信息，并同步团队/批次下发范围；
   * 不再校验项目启动状态（任务创建时已校验）。
   */
  @Override
  @Transactional
  public TaskVO updateTask(TaskSaveDTO dto) {
    if (dto.getId() == null) {
      throw new ServiceException("任务ID不能为空");
    }
    Long projectId = resolveTaskProjectId(dto);
    Long classSetId = firstClassSetId(projectId);
    List<Long> teamIds = resolveTaskTeamIds(dto);
    Long teamId = teamIds.isEmpty() ? null : teamIds.get(0);
    List<Long> batchIds = resolveTaskBatchIds(projectId, dto.getBatchIds());
    assertBatchesNotOccupied(dto.getId(), batchIds);
    String dataType = dto.getDataType() == null ? MediaType.IMAGE : dto.getDataType();
    int assetCount = dto.getAssetCount() != null ? dto.getAssetCount() : countAssetsOfBatches(batchIds);
    LabelTask task = new LabelTask();
    task.setId(dto.getId());
    task.setProjectId(projectId);
    task.setClassSetId(classSetId);
    task.setTaskName(dto.getTaskName());
    task.setDataType(dataType);
    task.setTeamId(teamId);
    task.setAssetCount(assetCount);
    // 编辑任务时同步预标注引入开关（未传保持关闭）
    task.setPreAnnotationEnabled(normalizePreAnnotationEnabled(dto.getPreAnnotationEnabled()));
    task.setUserId(currentUserId());
    taskMapper.updateTask(task);
    syncTaskTeams(dto.getId(), teamIds);
    syncTaskBatches(dto.getId(), batchIds);
    return task(dto.getId());
  }

  /** 预标注开关值规范化：仅 1 视为开启，其余（0/空）一律关闭。 */
  private Integer normalizePreAnnotationEnabled(Integer value) {
    return Integer.valueOf(1).equals(value) ? 1 : 0;
  }

  /** 解析任务所属项目：优先 projectId，兼容旧前端仅传 projectName。 */
  private Long resolveTaskProjectId(TaskSaveDTO dto) {
    return dto.getProjectId() != null
        ? dto.getProjectId()
        : findProjectId(dto.getProjectName(), 1L);
  }

  /** 解析任务承接团队：优先 teamIds，兼容旧前端仅传 teamName（按名称解析并自动创建）。 */
  private List<Long> resolveTaskTeamIds(TaskSaveDTO dto) {
    List<Long> teamIds = validTeamIds(dto.getTeamIds());
    if (teamIds.isEmpty()) {
      Long legacyTeamId = findOrCreateTeam(dto.getTeamName());
      if (legacyTeamId != null) {
        teamIds = List.of(legacyTeamId);
      }
    }
    return teamIds;
  }

  /**
   * 创建任务前置校验：关联项目必须存在且处于「进行中」。
   * 只有启动过的项目才允许下发任务——启动时已保证参与团队、标注标签、标注数据齐备。
   */
  private void assertProjectStarted(Long projectId) {
    String status = projectId == null ? null : projectMapper.selectProjectStatus(projectId);
    if (status == null || status.isBlank()) {
      throw new ServiceException("关联项目不存在，请重新选择");
    }
    if (ProjectStatus.ARCHIVED.equals(status)) {
      throw new ServiceException("关联项目已归档，不能创建任务");
    }
    if (ProjectStatus.COMPLETED.equals(status)) {
      throw new ServiceException("关联项目已完成，不能创建任务");
    }
    if (!ProjectStatus.RUNNING.equals(status)) {
      throw new ServiceException("关联项目尚未启动，请先在项目详情中配置参与团队、标注标签与标注数据并启动项目");
    }
  }

  /** 校验并归一化下发批次：必须属于所选项目，保序去重。 */
  private List<Long> resolveTaskBatchIds(Long projectId, List<Long> batchIds) {
    List<Long> result = new ArrayList<>();
    if (batchIds == null) {
      return result;
    }
    for (Long batchId : batchIds) {
      if (batchId == null || result.contains(batchId)) {
        continue;
      }
      Long ownerProjectId = labelCommonMapper.selectBatchProjectId(batchId);
      if (ownerProjectId == null || !ownerProjectId.equals(projectId)) {
        throw new ServiceException("所选数据批次不存在或不属于该项目");
      }
      result.add(batchId);
    }
    return result;
  }

  /**
   * 业务规则：同一数据批次只允许下发一次。已被其它任务下发的批次不允许再次选择；
   * 编辑任务时排除任务自身，避免把本任务已占用的批次误判为冲突。
   */
  private void assertBatchesNotOccupied(Long currentTaskId, List<Long> batchIds) {
    if (batchIds == null || batchIds.isEmpty()) {
      return;
    }
    List<BatchOccupancyVO> occupied = taskMapper.selectOccupiedBatches(batchIds, currentTaskId);
    if (occupied == null || occupied.isEmpty()) {
      return;
    }
    String detail = occupied.stream()
        .map(item -> (item.getBatchName() == null ? "批次" + item.getBatchId() : item.getBatchName())
            + "（已下发至任务：" + (item.getTaskName() == null ? item.getTaskNo() : item.getTaskName()) + "）")
        .collect(Collectors.joining("；"));
    throw new ServiceException("数据批次已下发过任务，不能重复下发：" + detail);
  }

  @Override
  public List<BatchOccupancyVO> batchOccupancy(Long projectId) {
    if (projectId == null) {
      return new ArrayList<>();
    }
    return taskMapper.selectBatchOccupancy(projectId);
  }

  @Override
  public Long projectIdOfTask(Long taskId) {
    if (taskId == null) {
      return null;
    }
    return taskMapper.selectTaskProjectId(taskId);
  }

  private int countAssetsOfBatches(List<Long> batchIds) {
    if (batchIds == null || batchIds.isEmpty()) {
      return 0;
    }
    return taskMapper.countAssetsByBatches(batchIds);
  }

  /** 全量替换任务承接团队（先清空关联再写入）。 */
  private void syncTaskTeams(Long taskId, List<Long> teamIds) {
    taskMapper.deleteTaskTeams(taskId);
    if (teamIds == null) {
      return;
    }
    for (Long teamId : teamIds) {
      if (teamId != null) {
        taskMapper.insertTaskTeam(taskId, teamId, currentUserId());
      }
    }
  }

  /**
   * 同步任务下发批次：按「移出 / 新增」差异更新，替代原来的全删重插 + 逐条 insert。
   * task_id 是条目归属任务的唯一来源，批次变动时在本方法内成对维护：
   * 移出的批次解绑条目（unbindEntriesFromTask），新增的批次绑定条目（bindEntriesToTask）。
   * 并发重复下发由 batch_id 唯一键兜底。
   */
  private void syncTaskBatches(Long taskId, List<Long> batchIds) {
    Long userId = currentUserId();
    Set<Long> currentIds = new LinkedHashSet<>(taskMapper.selectTaskBatchIds(taskId));
    Set<Long> targetIds = new LinkedHashSet<>();
    if (batchIds != null) {
      for (Long batchId : batchIds) {
        if (batchId != null) {
          targetIds.add(batchId);
        }
      }
    }

    // 移出的批次：先解绑条目，再删关联，保证条目不再出现在本任务视图
    List<Long> removed = currentIds.stream()
        .filter(id -> !targetIds.contains(id))
        .collect(Collectors.toList());
    if (!removed.isEmpty()) {
      taskMapper.unbindEntriesFromTask(taskId, removed, userId);
      taskMapper.deleteTaskBatchesByIds(taskId, removed);
    }

    // 新增的批次：一条 INSERT 写入全部关联，避免逐条 insert 的 N 次往返
    List<Long> added = targetIds.stream()
        .filter(id -> !currentIds.contains(id))
        .collect(Collectors.toList());
    if (!added.isEmpty()) {
      try {
        taskMapper.insertTaskBatches(taskId, added, userId);
      } catch (DuplicateKeyException ex) {
        throw new ServiceException("数据批次已被其它任务下发，不能重复下发，请刷新后重试");
      }
      // 批次内的数据条目（数据集合包）同步归属到任务
      taskMapper.bindEntriesToTask(taskId, added, userId);
    }
  }

  /** 为任务行补充下发团队与批次（列表/详情复用）。 */
  private void fillTaskScope(List<TaskVO> rows) {
    if (rows == null || rows.isEmpty()) {
      return;
    }
    List<Long> ids =
        rows.stream().map(TaskVO::getId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
    if (!ids.isEmpty()) {
      Map<Long, TaskVO> byId =
          rows.stream()
              .filter(r -> r.getId() != null)
              .collect(Collectors.toMap(TaskVO::getId, r -> r, (a, b) -> a));
      for (Map<String, Object> rel : taskMapper.selectTaskTeams(ids)) {
        TaskVO vo = byId.get(asLong(rel.get("taskId")));
        if (vo == null) {
          continue;
        }
        if (vo.getTeamIds() == null) {
          vo.setTeamIds(new ArrayList<>());
          vo.setTeamNames(new ArrayList<>());
        }
        vo.getTeamIds().add(asLong(rel.get("teamId")));
        vo.getTeamNames().add(stringValue(rel.get("teamName")));
      }
      for (Map<String, Object> rel : taskMapper.selectTaskBatches(ids)) {
        TaskVO vo = byId.get(asLong(rel.get("taskId")));
        if (vo == null) {
          continue;
        }
        if (vo.getBatchIds() == null) {
          vo.setBatchIds(new ArrayList<>());
          vo.setBatchNames(new ArrayList<>());
        }
        vo.getBatchIds().add(asLong(rel.get("batchId")));
        vo.getBatchNames().add(stringValue(rel.get("batchName")));
      }
    }
    for (TaskVO vo : rows) {
      if (vo.getTeamIds() == null) {
        vo.setTeamIds(new ArrayList<>());
      }
      if (vo.getBatchIds() == null) {
        vo.setBatchIds(new ArrayList<>());
      }
    }
  }

  // ---------------- 作业（job） ----------------

  /** 作业列表：按任务过滤（taskId 为空时为全局作业，供标注/审核/看板复用）。 */
  @Override
  public List<JobVO> jobs(Long taskId) {
    return taskMapper.selectJobs(taskId);
  }

  @Override
  public List<JobVO> jobsByFilter(Long assigneeId, List<String> statuses, String status) {
    return taskMapper.selectJobsByFilter(assigneeId, statuses, status);
  }

  @Override
  public JobVO job(Long id) {
    JobVO job = taskMapper.selectJob(id);
    if (job == null) {
      throw new ServiceException("作业不存在");
    }
    return job;
  }

  @Override
  @Transactional
  public JobVO assignJob(Long id, JobAssignDTO dto) {
    Long assigneeId = findUserId(dto.getAssigneeName(), 3L);
    Long reviewerId = findUserId(dto.getReviewerName(), 4L);
    Long taskId = taskMapper.selectJobTaskId(id);
    taskMapper.updateJobAssignee(id, assigneeId, reviewerId, currentUserId());
    // 任务状态随作业分配进度联动（全部分配完成后进入标注中）
    syncTaskStatus(taskId);
    createNotification(assigneeId, "新作业已分配", "作业 " + job(id).getJobNo() + " 等待标注", "job", id);
    return job(id);
  }

  @Override
  public JobVO updateJobStatus(Long id, JobStatusDTO dto) {
    taskMapper.updateJobStatus(id, dto.getStatus(), currentUserId());
    return job(id);
  }

  /**
   * 根据任务下所有作业的状态，聚合刷新任务状态。
   * 流转优先级：待分配 → 标注中 → 已退回 → 待审核 → 待验收 → 已完成。
   * 供标注/审核流程提交后回调使用。
   *
   * <p>仍存在「已放弃」条目时任务不置为已完成：条目已从作业摘除，
   * 需等待放弃人恢复后继续标注，故按「标注中」兜底。</p>
   */
  @Override
  public void syncTaskStatus(Long taskId) {
    if (taskId == null) {
      return;
    }
    List<String> statuses = taskMapper.selectJobStatuses(taskId);
    boolean hasAbandoned = taskMapper.countAbandonedEntries(taskId) > 0;
    String taskStatus;
    if (statuses.isEmpty() || statuses.contains(LabelJobStatus.PENDING_ASSIGN)) {
      taskStatus = hasAbandoned ? LabelJobStatus.LABELING : LabelJobStatus.PENDING_ASSIGN;
    } else if (statuses.contains(LabelJobStatus.PENDING_LABEL)
        || statuses.contains(LabelJobStatus.LABELING)) {
      taskStatus = LabelJobStatus.LABELING;
    } else if (statuses.contains(LabelJobStatus.REVIEW_REJECTED)) {
      taskStatus = LabelJobStatus.REVIEW_REJECTED;
    } else if (statuses.contains(LabelJobStatus.PENDING_REVIEW)) {
      taskStatus = LabelJobStatus.PENDING_REVIEW;
    } else if (statuses.contains(LabelJobStatus.PENDING_ACCEPTANCE)) {
      taskStatus = LabelJobStatus.PENDING_ACCEPTANCE;
    } else {
      taskStatus = hasAbandoned ? LabelJobStatus.LABELING : LabelJobStatus.COMPLETED;
    }
    taskMapper.updateTaskStatus(taskId, taskStatus, currentUserId());
    // 任务状态刷新后同步工作流节点：标注全部完成→进入审核节点；审核逐级通过→合格数据
    workflowService.syncTaskNode(taskId);
  }
}
