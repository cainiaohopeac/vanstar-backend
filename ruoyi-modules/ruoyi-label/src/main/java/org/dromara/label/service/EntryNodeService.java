package org.dromara.label.service;

import java.util.List;

/**
 * 数据条目节点状态服务：按工作流节点跟踪条目领取/跳过/放弃/完成状态。
 */
public interface EntryNodeService {

  /**
   * 确保任务下指定条目的节点记录存在（不存在则创建 pending）。
   */
  void ensureNode(Long entryId, Long taskId, String nodeKey, String nodeType);

  /**
   * 初始化任务下全部已提交条目进入某个新节点（池子 pending）。
   */
  void initNodeForTask(Long taskId, String nodeKey, String nodeType);

  /**
   * 领取节点。
   */
  void claim(Long entryId, String nodeKey, Long operatorId);

  /**
   * 跳过节点。
   */
  void skip(Long entryId, String nodeKey, Long skipBy);

  /**
   * 放弃节点。
   */
  void abandon(Long entryId, String nodeKey, Long abandonBy);

  /**
   * 恢复节点（跳过/放弃 → 之前的状态）。
   */
  void restore(Long entryId, String nodeKey);

  /**
   * 按条目完成其被领取的节点记录（不限节点，审核通过时使用）。
   */
  void markDoneClaimed(Long entryId);

  /**
   * 按条目重置其被领取的节点记录回池子（审核驳回时使用）。
   */
  void resetPendingClaimed(Long entryId);

  /**
   * 任务级清扫：某节点全部条目审完（工作流推进时兜底，兼容作业级旧流程）。
   */
  void completeNodeForTask(Long taskId, String nodeKey);

  /**
   * 任务级同步：条目退回修订阶段时，按条目审核状态重排全部审核节点记录。
   */
  void syncReviewNodesForTask(Long taskId);

  /**
   * 批量同步条目冗余的 latest_node_id（MAX 语义）：批量 INSERT 节点记录后调用，
   * 让总览主键 JOIN 命中最新流转记录而非过期指针。
   */
  void syncLatestNodeIds(List<Long> entryIds, Long taskId);
}
