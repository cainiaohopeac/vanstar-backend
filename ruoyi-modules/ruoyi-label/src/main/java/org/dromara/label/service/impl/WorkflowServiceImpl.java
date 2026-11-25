package org.dromara.label.service.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.label.common.LabelJobStatus;
import org.dromara.label.dto.WorkflowNodeItemDTO;
import org.dromara.label.dto.WorkflowNodeSaveDTO;
import org.dromara.label.entity.LabelWorkflowNode;
import org.dromara.label.mapper.WorkflowNodeMapper;
import org.dromara.label.service.BaseService;
import org.dromara.label.service.EntryNodeService;
import org.dromara.label.service.WorkflowService;
import org.dromara.label.vo.WorkflowNodeVO;
import org.dromara.label.vo.WorkflowTaskNodeVO;
import org.dromara.label.vo.WorkflowTaskProgressVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 项目工作流域服务实现：节点配置维护（项目隔离）+ 任务节点流转推进。
 * SQL 统一收敛至 WorkflowNodeMapper.xml。
 */
@Service
public class WorkflowServiceImpl extends BaseService implements WorkflowService {

  /** 默认流程的排序步长（预留中间插入空间） */
  private static final int SORT_STEP = 10;

  /** 单个批注类型最大长度（与 label_object_comment.comment_type 列宽对齐） */
  private static final int MAX_COMMENT_TYPE_LENGTH = 32;

  /** 一个审核节点最多配置的批注类型数量（防止前端误提交超长列表） */
  private static final int MAX_COMMENT_TYPE_COUNT = 20;

  @Autowired
  private WorkflowNodeMapper workflowNodeMapper;

  @Autowired
  private EntryNodeService entryNodeService;

  // ---------------- 节点配置 ----------------

  @Override
  public List<WorkflowNodeVO> nodes(Long projectId) {
    if (projectId == null) {
      return new ArrayList<>();
    }
    return toVOs(ensureNodes(projectId));
  }

  @Override
  @Transactional
  public List<WorkflowNodeVO> saveNodes(WorkflowNodeSaveDTO dto) {
    if (dto == null || dto.getProjectId() == null) {
      throw new ServiceException("请选择项目");
    }
    Long projectId = dto.getProjectId();
    if (workflowNodeMapper.countProject(projectId) == 0) {
      throw new ServiceException("项目不存在或已归档");
    }
    // 先确保默认流程存在，避免首次保存时固定节点缺失
    ensureNodes(projectId);
    long userId = currentUserId();
    List<WorkflowNodeItemDTO> items = dto.getNodes() == null ? List.of() : dto.getNodes();
    Set<String> used = new HashSet<>();
    List<String> keepKeys = new ArrayList<>();
    int index = 0;
    int nextReview = nextReviewIndex(projectId);
    boolean hasStart = false;
    boolean hasLabel = false;
    boolean hasEnd = false;
    for (WorkflowNodeItemDTO item : items) {
      if (item == null) {
        continue;
      }
      index++;
      String type = resolveType(item.getNodeKey(), item.getNodeType());
      if (NODE_TYPE_START.equals(type)) {
        hasStart = true;
      } else if (NODE_TYPE_LABEL.equals(type)) {
        hasLabel = true;
      } else if (NODE_TYPE_END.equals(type)) {
        hasEnd = true;
      }
      String key = item.getNodeKey() == null ? "" : item.getNodeKey().trim();
      if (key.isEmpty()) {
        // 新增节点：固定类型用固定 key，审核节点自动生成 review_N
        key = NODE_TYPE_REVIEW.equals(type) ? "review_" + (nextReview++) : keyOfType(type);
      }
      if (key == null || !used.add(key)) {
        continue;
      }
      upsertNode(buildNode(projectId, key, item.getNodeName(), type, index * SORT_STEP,
          item.getRemark(), item.getCommentTypes(), userId));
      keepKeys.add(key);
    }
    // 固定节点兜底：缺失时补齐，保证流程完整（原始数据 → 标注 → … → 合格数据）
    if (!hasStart) {
      keepKeys.add(upsertNode(buildNode(projectId, KEY_RAW_DATA, "原始数据", NODE_TYPE_START, 1,
          "项目数据起点", null, userId)));
    }
    if (!hasLabel) {
      keepKeys.add(upsertNode(buildNode(projectId, KEY_LABEL, "标注", NODE_TYPE_LABEL, SORT_STEP,
          "标注员领取并提交标注", null, userId)));
    }
    if (!hasEnd) {
      keepKeys.add(upsertNode(buildNode(projectId, KEY_QUALIFIED, "合格数据", NODE_TYPE_END, 999999,
          "全部审核通过后产出", null, userId)));
    }
    workflowNodeMapper.softDeleteNodesExcept(projectId, keepKeys, userId);
    writeOperationLog("SAVE_WORKFLOW_NODES", "project", projectId, Map.of("count", keepKeys.size()));
    return toVOs(workflowNodeMapper.selectNodes(projectId));
  }

  @Override
  @Transactional
  public void initDefault(Long projectId) {
    if (projectId == null || workflowNodeMapper.countNodes(projectId) > 0) {
      return;
    }
    long userId = currentUserId();
    upsertNode(buildNode(projectId, KEY_RAW_DATA, "原始数据", NODE_TYPE_START, SORT_STEP,
        "项目数据起点", null, userId));
    upsertNode(buildNode(projectId, KEY_LABEL, "标注", NODE_TYPE_LABEL, SORT_STEP * 2,
        "标注员领取并提交标注", null, userId));
    upsertNode(buildNode(projectId, "review_" + 1, "审核", NODE_TYPE_REVIEW, SORT_STEP * 3,
        "审核员领取并审核，可驳回后重新提交", DEFAULT_COMMENT_TYPES, userId));
    upsertNode(buildNode(projectId, KEY_QUALIFIED, "合格数据", NODE_TYPE_END, SORT_STEP * 4,
        "全部审核通过后产出合格数据", null, userId));
    writeOperationLog("INIT_WORKFLOW", "project", projectId, Map.of("projectId", projectId));
  }

  /** 构建节点实体：统一补齐默认名称与（审核节点的）批注类型，审计字段填当前操作人。 */
  private LabelWorkflowNode buildNode(Long projectId, String nodeKey, String nodeName, String nodeType,
      int sortOrder, String remark, List<String> commentTypes, long userId) {
    LabelWorkflowNode node = new LabelWorkflowNode();
    node.setProjectId(projectId);
    node.setNodeKey(nodeKey);
    node.setNodeName(nodeName == null || nodeName.isBlank() ? defaultName(nodeType) : nodeName.trim());
    node.setNodeType(nodeType);
    node.setSortOrder(sortOrder);
    node.setRemark(remark);
    // 只有审核节点才存批注类型，避免固定节点被写入无意义数据
    node.setCommentTypes(NODE_TYPE_REVIEW.equals(nodeType) ? toJson(normalizeCommentTypes(commentTypes)) : null);
    node.setCreatedBy(userId);
    node.setUpdatedBy(userId);
    return node;
  }

  /** 新增/更新节点（同 key 幂等覆盖），返回节点标识。 */
  private String upsertNode(LabelWorkflowNode node) {
    workflowNodeMapper.insertNode(node);
    return node.getNodeKey();
  }

  /**
   * 规整批注类型列表：去空白、去重、单个最长 32 字、最多 20 个，空列表回落到默认五类。
   * 前端可直接提交用户输入，防御性收敛在这里做，避免脏数据落库。
   */
  private List<String> normalizeCommentTypes(List<String> types) {
    List<String> out = new ArrayList<>();
    if (types != null) {
      for (String raw : types) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
          continue;
        }
        if (text.length() > MAX_COMMENT_TYPE_LENGTH) {
          text = text.substring(0, MAX_COMMENT_TYPE_LENGTH);
        }
        if (!out.contains(text)) {
          out.add(text);
        }
        if (out.size() >= MAX_COMMENT_TYPE_COUNT) {
          break;
        }
      }
    }
    return out.isEmpty() ? DEFAULT_COMMENT_TYPES : out;
  }

  /** 解析库里的批注类型 JSON（非法/为空时回落默认五类）。 */
  private List<String> parseCommentTypes(String json) {
    Object parsed = parseJson(json);
    List<String> out = new ArrayList<>();
    if (parsed instanceof List<?> raw) {
      for (Object item : raw) {
        String text = item == null ? "" : String.valueOf(item).trim();
        if (!text.isEmpty() && !out.contains(text)) {
          out.add(text);
        }
      }
    }
    return out.isEmpty() ? DEFAULT_COMMENT_TYPES : out;
  }

  @Override
  public Long projectIdOfTask(Long taskId) {
    if (taskId == null) {
      return null;
    }
    return workflowNodeMapper.selectTaskProjectId(taskId);
  }

  @Override
  public List<String> commentTypesOfNode(Long projectId, String nodeKey) {
    if (projectId == null) {
      return DEFAULT_COMMENT_TYPES;
    }
    List<LabelWorkflowNode> nodes = workflowNodeMapper.selectNodes(projectId);
    if (nodes == null || nodes.isEmpty()) {
      return DEFAULT_COMMENT_TYPES;
    }
    String key = nodeKey == null ? "" : nodeKey.trim();
    // 1) 指定节点就是审核节点：用它自己配置的类型
    for (LabelWorkflowNode node : nodes) {
      if (NODE_TYPE_REVIEW.equals(node.getNodeType()) && key.equals(node.getNodeKey())) {
        return parseCommentTypes(node.getCommentTypes());
      }
    }
    // 2) 当前不在审核节点（或节点已删除）：退到第一个审核节点的配置
    for (LabelWorkflowNode node : nodes) {
      if (NODE_TYPE_REVIEW.equals(node.getNodeType())) {
        return parseCommentTypes(node.getCommentTypes());
      }
    }
    return DEFAULT_COMMENT_TYPES;
  }

  /** 保证项目已有节点配置，返回按顺序排列的节点列表。 */
  private List<LabelWorkflowNode> ensureNodes(Long projectId) {
    if (projectId == null) {
      return new ArrayList<>();
    }
    if (workflowNodeMapper.countNodes(projectId) == 0) {
      initDefault(projectId);
    }
    List<LabelWorkflowNode> nodes = workflowNodeMapper.selectNodes(projectId);
    return nodes == null ? new ArrayList<>() : nodes;
  }

  /** 现有审核节点最大序号 + 1（用于生成新的 review_N）。 */
  private int nextReviewIndex(Long projectId) {
    int max = 0;
    List<LabelWorkflowNode> nodes = workflowNodeMapper.selectNodes(projectId);
    if (nodes == null) {
      return 1;
    }
    for (LabelWorkflowNode node : nodes) {
      String key = node.getNodeKey();
      if (key != null && key.startsWith("review_")) {
        try {
          max = Math.max(max, Integer.parseInt(key.substring("review_".length())));
        } catch (NumberFormatException ignored) {
          // 自定义 key 不参与序号计算
        }
      }
    }
    return max + 1;
  }

  /** 解析节点类型：显式类型优先，其次按固定 key 推断，默认按审核节点处理。 */
  private String resolveType(String nodeKey, String nodeType) {
    String type = nodeType == null ? "" : nodeType.trim().toLowerCase();
    if (NODE_TYPE_START.equals(type) || NODE_TYPE_LABEL.equals(type)
        || NODE_TYPE_REVIEW.equals(type) || NODE_TYPE_END.equals(type)) {
      return type;
    }
    String key = nodeKey == null ? "" : nodeKey.trim();
    if (KEY_RAW_DATA.equals(key)) {
      return NODE_TYPE_START;
    }
    if (KEY_LABEL.equals(key)) {
      return NODE_TYPE_LABEL;
    }
    if (KEY_QUALIFIED.equals(key)) {
      return NODE_TYPE_END;
    }
    return NODE_TYPE_REVIEW;
  }

  private String keyOfType(String type) {
    if (NODE_TYPE_START.equals(type)) {
      return KEY_RAW_DATA;
    }
    if (NODE_TYPE_LABEL.equals(type)) {
      return KEY_LABEL;
    }
    if (NODE_TYPE_END.equals(type)) {
      return KEY_QUALIFIED;
    }
    return null;
  }

  private String defaultName(String type) {
    if (NODE_TYPE_START.equals(type)) {
      return "原始数据";
    }
    if (NODE_TYPE_LABEL.equals(type)) {
      return "标注";
    }
    if (NODE_TYPE_END.equals(type)) {
      return "合格数据";
    }
    return "审核";
  }

  private List<WorkflowNodeVO> toVOs(List<LabelWorkflowNode> nodes) {
    List<WorkflowNodeVO> list = new ArrayList<>();
    if (nodes == null) {
      return list;
    }
    for (LabelWorkflowNode node : nodes) {
      WorkflowNodeVO vo = new WorkflowNodeVO();
      vo.setId(node.getId());
      vo.setProjectId(node.getProjectId());
      vo.setNodeKey(node.getNodeKey());
      vo.setNodeName(node.getNodeName());
      vo.setNodeType(node.getNodeType());
      vo.setSortOrder(node.getSortOrder());
      vo.setRemark(node.getRemark());
      vo.setFixed(!NODE_TYPE_REVIEW.equals(node.getNodeType()));
      // 非审核节点没有批注类型（返回空列表，前端不渲染该列）
      vo.setCommentTypes(NODE_TYPE_REVIEW.equals(node.getNodeType())
          ? parseCommentTypes(node.getCommentTypes())
          : new ArrayList<>());
      list.add(vo);
    }
    return list;
  }

  // ---------------- 任务节点流转 ----------------

  @Override
  public WorkflowTaskProgressVO taskProgress(Long taskId) {
    if (taskId == null) {
      throw new ServiceException("任务不存在");
    }
    Long projectId = workflowNodeMapper.selectTaskProjectId(taskId);
    if (projectId == null) {
      throw new ServiceException("任务不存在");
    }
    List<LabelWorkflowNode> nodes = ensureNodes(projectId);
    String taskStatus = workflowNodeMapper.selectTaskStatus(taskId);
    String currentNode = workflowNodeMapper.selectTaskCurrentNode(taskId);
    if (currentNode == null || currentNode.isBlank()) {
      currentNode = KEY_LABEL;
    }
    String effective = TASK_STATUS_REJECTED.equals(taskStatus) ? KEY_LABEL : currentNode;
    int effectiveOrder = orderOf(nodes, effective);

    Map<String, Object> stats = workflowNodeMapper.selectEntryFlowStats(taskId);
    long total = longValue(stats, "total");
    long working = longValue(stats, "workingCount");
    long rejected = longValue(stats, "rejectedCount");
    long labeled = longValue(stats, "labeledCount");
    long passed = longValue(stats, "passedCount");
    long waiting = longValue(stats, "waitingReviewCount");

    WorkflowTaskProgressVO vo = new WorkflowTaskProgressVO();
    vo.setTaskId(taskId);
    vo.setTaskName(workflowNodeMapper.selectTaskName(taskId));
    vo.setTaskStatus(taskStatus);
    vo.setProjectId(projectId);
    vo.setCurrentNodeKey(currentNode);
    vo.setEffectiveNodeKey(effective);
    List<WorkflowTaskNodeVO> nodeVOs = new ArrayList<>();
    for (LabelWorkflowNode node : nodes) {
      WorkflowTaskNodeVO nodeVO = new WorkflowTaskNodeVO();
      nodeVO.setNodeKey(node.getNodeKey());
      nodeVO.setNodeName(node.getNodeName());
      nodeVO.setNodeType(node.getNodeType());
      nodeVO.setSortOrder(node.getSortOrder());
      int order = node.getSortOrder() == null ? 0 : node.getSortOrder();
      String state = order < effectiveOrder ? STATE_DONE : (order == effectiveOrder ? STATE_CURRENT : STATE_TODO);
      nodeVO.setState(state);
      nodeVO.setDescription(describe(node, state, taskStatus, total, labeled, passed, working, rejected, waiting));
      nodeVO.setTotal(statOfType(node.getNodeType(), total, labeled));
      nodeVO.setDoneCount(doneOfType(node.getNodeType(), total, labeled, passed, state));
      nodeVOs.add(nodeVO);
      if (order == effectiveOrder && vo.getCurrentNodeName() == null) {
        vo.setCurrentNodeName(node.getNodeName());
        vo.setDescription(nodeVO.getDescription());
      }
    }
    vo.setNodes(nodeVOs);
    if (vo.getCurrentNodeName() == null && !nodes.isEmpty()) {
      vo.setCurrentNodeName(nodes.get(0).getNodeName());
    }
    return vo;
  }

  /** 节点计数基数：起点/终点看整体，标注/审核看已标注数。 */
  private Long statOfType(String nodeType, long total, long labeled) {
    if (NODE_TYPE_LABEL.equals(nodeType) || NODE_TYPE_REVIEW.equals(nodeType)) {
      return labeled;
    }
    return total;
  }

  private Long doneOfType(String nodeType, long total, long labeled, long passed, String state) {
    if (NODE_TYPE_START.equals(nodeType)) {
      return STATE_TODO.equals(state) ? 0L : total;
    }
    if (NODE_TYPE_LABEL.equals(nodeType)) {
      return labeled;
    }
    if (NODE_TYPE_REVIEW.equals(nodeType)) {
      return passed;
    }
    return passed;
  }

  private String describe(LabelWorkflowNode node, String state, String taskStatus, long total,
      long labeled, long passed, long working, long rejected, long waiting) {
    String type = node.getNodeType();
    if (NODE_TYPE_START.equals(type)) {
      return STATE_TODO.equals(state) ? "未开始" : "数据已导入";
    }
    if (NODE_TYPE_LABEL.equals(type)) {
      if (TASK_STATUS_REJECTED.equals(taskStatus)) {
        return "已退回，待修订（" + rejected + " 条）";
      }
      if (STATE_DONE.equals(state)) {
        return "标注已完成";
      }
      if (STATE_CURRENT.equals(state)) {
        return working > 0 ? "标注中（剩余 " + working + " 条）" : "待标注";
      }
      return "未开始";
    }
    if (NODE_TYPE_END.equals(type)) {
      if (STATE_DONE.equals(state) || STATE_CURRENT.equals(state)) {
        return "已产出合格数据（" + passed + "/" + total + "）";
      }
      return "未开始";
    }
    // 审核节点
    if (STATE_DONE.equals(state)) {
      return "本轮审核已通过";
    }
    if (STATE_CURRENT.equals(state)) {
      if (waiting > 0) {
        return "待审核 / 审核中（" + passed + "/" + labeled + " 已通过）";
      }
      return "待审核";
    }
    return "未开始";
  }

  @Override
  public void syncTaskNode(Long taskId) {
    if (taskId == null) {
      return;
    }
    Long projectId = workflowNodeMapper.selectTaskProjectId(taskId);
    if (projectId == null) {
      return;
    }
    List<LabelWorkflowNode> nodes = ensureNodes(projectId);
    if (nodes.isEmpty()) {
      return;
    }
    Map<String, Object> stats = workflowNodeMapper.selectEntryFlowStats(taskId);
    long total = longValue(stats, "total");
    if (total <= 0) {
      return;
    }
    long working = longValue(stats, "workingCount");
    long rejected = longValue(stats, "rejectedCount");
    long labeled = longValue(stats, "labeledCount");
    long passed = longValue(stats, "passedCount");
    long waiting = longValue(stats, "waitingReviewCount");
    String currentKey = workflowNodeMapper.selectTaskCurrentNode(taskId);
    if (currentKey == null || currentKey.isBlank()) {
      currentKey = KEY_LABEL;
    }
    LabelWorkflowNode current = findNode(nodes, currentKey);
    String endKey = endKeyOf(nodes);
    String targetKey = null;
    if (working > 0 || rejected > 0 || labeled <= 0) {
      // 仍在标注/修订阶段：节点保持不变，「已退回」由任务状态表达；
      // 同时按条目审核状态重排审核节点记录（持有的领取保留、已审完置完成、其余回池子）
      entryNodeService.syncReviewNodesForTask(taskId);
      targetKey = null;
    } else if (passed >= labeled) {
      // 本级审核已全部通过 → 进入下一级审核节点，或产出合格数据
      if (current != null && NODE_TYPE_REVIEW.equals(current.getNodeType())) {
        LabelWorkflowNode next = nextReviewNode(nodes, current.getSortOrder());
        if (next != null) {
          targetKey = next.getNodeKey();
          workflowNodeMapper.resetEntryReviewForNextNode(taskId, currentUserId());
          workflowNodeMapper.syncAssetsReviewForNextNode(taskId, currentUserId());
        } else {
          targetKey = endKey;
        }
      } else {
        targetKey = endKey;
      }
    } else if (waiting > 0
        && (current == null || !NODE_TYPE_REVIEW.equals(current.getNodeType()))) {
      // 已全部标注、等待审核：推进到第一个审核节点
      LabelWorkflowNode first = firstReviewNode(nodes);
      targetKey = first != null ? first.getNodeKey() : endKey;
    }
    long userId = currentUserId();
    if (targetKey != null && !targetKey.equals(currentKey)) {
      // 离开旧节点：旧节点记录全量置完成（兜底作业级旧流程未逐条维护的场景）
      if (current != null && NODE_TYPE_REVIEW.equals(current.getNodeType())) {
        entryNodeService.completeNodeForTask(taskId, currentKey);
      }
      workflowNodeMapper.updateTaskCurrentNode(taskId, targetKey, userId);
      // 进入新节点时，为任务下全部已提交条目初始化该节点的池子记录
      if (!targetKey.equals(endKey)) {
        entryNodeService.initNodeForTask(taskId, targetKey, NODE_TYPE_REVIEW);
      }
      writeOperationLog("TASK_NODE_CHANGE", "task", taskId,
          Map.of("from", currentKey, "to", targetKey));
      currentKey = targetKey;
    }
    if (endKey.equals(currentKey)) {
      // 全部审核节点通过 = 任务完成（作业同步置为已完成，与任务终态一致）
      workflowNodeMapper.completeJobsByTask(taskId, userId);
      if (!LabelJobStatus.COMPLETED.equals(workflowNodeMapper.selectTaskStatus(taskId))) {
        workflowNodeMapper.updateTaskStatusCompleted(taskId, userId);
      }
      // 同步任务完成数：条目级审核通过即完成，与作业级验收（acceptReview）统计口径保持一致
      workflowNodeMapper.updateTaskCompletedCount(taskId, (int) passed, userId);
    }
  }

  @Override
  public String effectiveNodeKey(Long taskId) {
    if (taskId == null) {
      return KEY_LABEL;
    }
    String status = workflowNodeMapper.selectTaskStatus(taskId);
    if (TASK_STATUS_REJECTED.equals(status)) {
      return KEY_LABEL;
    }
    String key = workflowNodeMapper.selectTaskCurrentNode(taskId);
    return key == null || key.isBlank() ? KEY_LABEL : key;
  }

  // ---------------- 内部工具 ----------------

  private LabelWorkflowNode findNode(List<LabelWorkflowNode> nodes, String nodeKey) {
    for (LabelWorkflowNode node : nodes) {
      if (node.getNodeKey() != null && node.getNodeKey().equals(nodeKey)) {
        return node;
      }
    }
    return null;
  }

  private int orderOf(List<LabelWorkflowNode> nodes, String nodeKey) {
    LabelWorkflowNode node = findNode(nodes, nodeKey);
    if (node != null && node.getSortOrder() != null) {
      return node.getSortOrder();
    }
    return nodes.isEmpty() || nodes.get(0).getSortOrder() == null ? 0 : nodes.get(0).getSortOrder();
  }

  private String endKeyOf(List<LabelWorkflowNode> nodes) {
    for (LabelWorkflowNode node : nodes) {
      if (NODE_TYPE_END.equals(node.getNodeType())) {
        return node.getNodeKey();
      }
    }
    return KEY_QUALIFIED;
  }

  private LabelWorkflowNode firstReviewNode(List<LabelWorkflowNode> nodes) {
    for (LabelWorkflowNode node : nodes) {
      if (NODE_TYPE_REVIEW.equals(node.getNodeType())) {
        return node;
      }
    }
    return null;
  }

  /** 取当前审核节点之后的第一个审核节点（按流转顺序）。 */
  private LabelWorkflowNode nextReviewNode(List<LabelWorkflowNode> nodes, Integer currentOrder) {
    int order = currentOrder == null ? 0 : currentOrder;
    LabelWorkflowNode next = null;
    for (LabelWorkflowNode node : nodes) {
      if (!NODE_TYPE_REVIEW.equals(node.getNodeType())) {
        continue;
      }
      int nodeOrder = node.getSortOrder() == null ? 0 : node.getSortOrder();
      if (nodeOrder > order && (next == null || nodeOrder < next.getSortOrder())) {
        next = node;
      }
    }
    return next;
  }

  private long longValue(Map<String, Object> row, String key) {
    if (row == null) {
      return 0;
    }
    Object value = mapValue(row, key);
    return value == null ? 0 : asLong(value);
  }

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
}
