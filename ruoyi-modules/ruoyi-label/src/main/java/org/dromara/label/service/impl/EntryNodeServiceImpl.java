package org.dromara.label.service.impl;

import java.util.List;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.label.common.EntryNodeStatus;
import org.dromara.label.entity.LabelEntryNode;
import org.dromara.label.mapper.EntryNodeMapper;
import org.dromara.label.service.BaseService;
import org.dromara.label.service.EntryNodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 数据条目节点状态服务实现。
 */
@Service
public class EntryNodeServiceImpl extends BaseService implements EntryNodeService {

  @Autowired
  private EntryNodeMapper entryNodeMapper;

  @Override
  public void ensureNode(Long entryId, Long taskId, String nodeKey, String nodeType) {
    if (entryId == null || taskId == null || nodeKey == null || nodeKey.isBlank()) {
      return;
    }
    long userId = currentUserId();
    LabelEntryNode exist = entryNodeMapper.selectByEntryAndNode(entryId, nodeKey);
    if (exist != null) {
      // 节点记录已存在，仍同步一次 latest_node_id（强兜底 stale 冗余字段）
      entryNodeMapper.updateEntryLatestNodeId(entryId, taskId, userId);
      return;
    }
    LabelEntryNode node = new LabelEntryNode();
    node.setEntryId(entryId);
    node.setTaskId(taskId);
    node.setNodeKey(nodeKey);
    node.setNodeType(nodeType == null || nodeType.isBlank() ? "review" : nodeType);
    node.setStatus(EntryNodeStatus.PENDING);
    node.setCreatedBy(userId);
    node.setUpdatedBy(userId);
    entryNodeMapper.insertIgnore(node);
    // INSERT IGNORE 后同步 entry.latest_node_id：刚插入 / 已忽略（其他线程兜底） 都幂等
    entryNodeMapper.updateEntryLatestNodeId(entryId, taskId, userId);
  }

  @Override
  public void initNodeForTask(Long taskId, String nodeKey, String nodeType) {
    if (taskId == null || nodeKey == null || nodeKey.isBlank()) {
      return;
    }
    long userId = currentUserId();
    // 守卫：已提交条目都有节点记录时直接返回，避免每次列表/统计请求都执行批量 INSERT
    if (entryNodeMapper.countMissingNodeRecords(taskId, nodeKey) == 0) {
      return;
    }
    entryNodeMapper.batchInsertPendingByTask(taskId, nodeKey,
        nodeType == null || nodeType.isBlank() ? "review" : nodeType, userId);
    // 批量 INSERT 后同步该 task + node_key 关联的全部 entry 的 latest_node_id
    entryNodeMapper.syncLatestNodeIdsByTaskNode(taskId, nodeKey, userId);
  }

  @Override
  public void syncLatestNodeIds(List<Long> entryIds, Long taskId) {
    if (entryIds == null || entryIds.isEmpty() || taskId == null) {
      return;
    }
    entryNodeMapper.updateEntryLatestNodeIds(entryIds, taskId, currentUserId());
  }

  @Override
  public void claim(Long entryId, String nodeKey, Long operatorId) {
    if (entryId == null || nodeKey == null || nodeKey.isBlank() || operatorId == null) {
      return;
    }
    int rows = entryNodeMapper.claim(entryId, nodeKey, operatorId, currentUserId());
    if (rows <= 0) {
      throw new ServiceException("该条目在当前节点已被领取或状态已变更，请刷新后重试");
    }
  }

  @Override
  public void skip(Long entryId, String nodeKey, Long skipBy) {
    if (entryId == null || nodeKey == null || nodeKey.isBlank() || skipBy == null) {
      return;
    }
    LabelEntryNode node = entryNodeMapper.selectByEntryAndNode(entryId, nodeKey);
    if (node == null) {
      throw new ServiceException("该条目在当前节点无状态记录，无法跳过");
    }
    String prev = node.getStatus();
    if (EntryNodeStatus.SKIPPED.equals(prev) || EntryNodeStatus.ABANDONED.equals(prev)) {
      throw new ServiceException("该条目在当前节点已被跳过或放弃");
    }
    int rows = entryNodeMapper.skip(entryId, nodeKey, prev, skipBy, currentUserId());
    if (rows <= 0) {
      throw new ServiceException("跳过失败，请刷新后重试");
    }
  }

  @Override
  public void abandon(Long entryId, String nodeKey, Long abandonBy) {
    if (entryId == null || nodeKey == null || nodeKey.isBlank() || abandonBy == null) {
      return;
    }
    LabelEntryNode node = entryNodeMapper.selectByEntryAndNode(entryId, nodeKey);
    if (node == null) {
      throw new ServiceException("该条目在当前节点无状态记录，无法放弃");
    }
    String prev = node.getStatus();
    if (EntryNodeStatus.SKIPPED.equals(prev) || EntryNodeStatus.ABANDONED.equals(prev)) {
      throw new ServiceException("该条目在当前节点已被跳过或放弃");
    }
    int rows = entryNodeMapper.abandon(entryId, nodeKey, prev, abandonBy, currentUserId());
    if (rows <= 0) {
      throw new ServiceException("放弃失败，请刷新后重试");
    }
  }

  @Override
  public void restore(Long entryId, String nodeKey) {
    if (entryId == null || nodeKey == null || nodeKey.isBlank()) {
      return;
    }
    LabelEntryNode node = entryNodeMapper.selectByEntryAndNode(entryId, nodeKey);
    if (node == null) {
      return;
    }
    String restored = node.getPrevStatus();
    if (restored == null || restored.isBlank()) {
      restored = EntryNodeStatus.PENDING;
    }
    int rows = entryNodeMapper.restore(entryId, nodeKey, restored, currentUserId());
    if (rows <= 0) {
      throw new ServiceException("恢复失败，请刷新后重试");
    }
  }

  @Override
  public void markDoneClaimed(Long entryId) {
    if (entryId == null) {
      return;
    }
    entryNodeMapper.markDoneClaimed(entryId, currentUserId());
  }

  @Override
  public void resetPendingClaimed(Long entryId) {
    if (entryId == null) {
      return;
    }
    entryNodeMapper.resetPendingClaimed(entryId, currentUserId());
  }

  @Override
  public void completeNodeForTask(Long taskId, String nodeKey) {
    if (taskId == null || nodeKey == null || nodeKey.isBlank()) {
      return;
    }
    entryNodeMapper.markAllDoneByTaskNode(taskId, nodeKey, currentUserId());
  }

  @Override
  public void syncReviewNodesForTask(Long taskId) {
    if (taskId == null) {
      return;
    }
    entryNodeMapper.syncTaskReviewNodes(taskId, currentUserId());
  }
}
