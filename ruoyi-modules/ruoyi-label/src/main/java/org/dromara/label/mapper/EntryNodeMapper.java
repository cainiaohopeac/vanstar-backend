package org.dromara.label.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.dromara.label.entity.LabelEntryNode;

/**
 * 数据条目节点状态 Mapper。
 *
 * <p>按工作流节点跟踪条目领取/跳过/放弃/完成状态。</p>
 */
public interface EntryNodeMapper {

  /**
   * 查询指定条目在指定节点的记录。
   */
  LabelEntryNode selectByEntryAndNode(@Param("entryId") Long entryId, @Param("nodeKey") String nodeKey);

  /**
   * 按条目集合查询全部节点记录（推导条目当前所处工作流节点用）。
   */
  List<LabelEntryNode> selectByEntryIds(@Param("entryIds") List<Long> entryIds);

  /**
   * 插入节点记录（幂等：已存在则忽略）。
   */
  int insertIgnore(LabelEntryNode node);

  /**
   * 领取节点：pending → claimed（兼容从 skipped/abandoned 回收），设置 operator_id。
   */
  int claim(@Param("entryId") Long entryId,
      @Param("nodeKey") String nodeKey,
      @Param("operatorId") Long operatorId,
      @Param("updatedBy") Long updatedBy);

  /**
   * 跳过节点：状态改为 skipped，记录 skip_by / skip_time / prev_status。
   */
  int skip(@Param("entryId") Long entryId,
      @Param("nodeKey") String nodeKey,
      @Param("prevStatus") String prevStatus,
      @Param("skipBy") Long skipBy,
      @Param("updatedBy") Long updatedBy);

  /**
   * 放弃节点：状态改为 abandoned，记录 abandon_by / abandon_time / prev_status。
   */
  int abandon(@Param("entryId") Long entryId,
      @Param("nodeKey") String nodeKey,
      @Param("prevStatus") String prevStatus,
      @Param("abandonBy") Long abandonBy,
      @Param("updatedBy") Long updatedBy);

  /**
   * 恢复节点：回滚状态到 prev_status，清空 skip/abandon 信息。
   */
  int restore(@Param("entryId") Long entryId,
      @Param("nodeKey") String nodeKey,
      @Param("restoredStatus") String restoredStatus,
      @Param("updatedBy") Long updatedBy);

  /**
   * 初始化任务下全部已提交条目在某个节点的池子记录（工作流推进到新节点）。
   */
  int batchInsertPendingByTask(@Param("taskId") Long taskId,
      @Param("nodeKey") String nodeKey,
      @Param("nodeType") String nodeType,
      @Param("userId") Long userId);

  /**
   * 已提交条目中缺该节点记录的数量（initNodeForTask 守卫，为 0 时跳过批量插入）。
   */
  int countMissingNodeRecords(@Param("taskId") Long taskId, @Param("nodeKey") String nodeKey);

  /**
   * 按条目完成其被领取的节点记录（不限节点，审核通过时使用）。
   */
  int markDoneClaimed(@Param("entryId") Long entryId, @Param("updatedBy") Long updatedBy);

  /**
   * 按条目重置其被领取的节点记录回池子（审核驳回时使用）。
   */
  int resetPendingClaimed(@Param("entryId") Long entryId, @Param("updatedBy") Long updatedBy);

  /**
   * 任务级清扫：某节点全部条目审完（工作流推进时兜底，兼容作业级旧流程）。
   */
  int markAllDoneByTaskNode(@Param("taskId") Long taskId,
      @Param("nodeKey") String nodeKey,
      @Param("updatedBy") Long updatedBy);

  /**
   * 任务级同步：条目退回修订阶段时，按条目审核状态重排全部审核节点记录。
   */
  int syncTaskReviewNodes(@Param("taskId") Long taskId, @Param("updatedBy") Long updatedBy);

  /**
   * 同步单条 entry 的 latest_node_id（指向该 entry 在指定 task 下最近一条 entry_node.id）。
   * INSERT label_entry_node 完成后调用，让总览 LEFT JOIN 主键命中而非相关子查询。
   */
  int updateEntryLatestNodeId(@Param("entryId") Long entryId,
      @Param("taskId") Long taskId,
      @Param("userId") Long userId);

  /**
   * 任务级批量同步：batchInsertPendingByTask 后调用，把该 task 下该 node_key 关联的
   * 全部 entry 的 latest_node_id 刷成 n.id（仅前进不回退）。
   */
  int syncLatestNodeIdsByTaskNode(@Param("taskId") Long taskId,
      @Param("nodeKey") String nodeKey,
      @Param("userId") Long userId);

  /**
   * 批量同步列表内 entry 的 latest_node_id（MAX 语义，指向该 entry 在指定 task 下
   * 最近一条 entry_node.id）。insertEntryNodeClaim 等批量 INSERT 后调用，
   * 首次插入指向新行，ON DUPLICATE 不换 id 时刷新幂等。
   */
  int updateEntryLatestNodeIds(@Param("entryIds") List<Long> entryIds,
      @Param("taskId") Long taskId,
      @Param("userId") Long userId);
}
