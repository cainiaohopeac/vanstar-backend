package org.dromara.label.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import org.dromara.label.entity.LabelWorkflowNode;

/**
 * 项目工作流节点域数据访问
 * （label_workflow_node / label_task.current_node_key / label_data_entry 流转统计）。
 *
 * <p>SQL 统一收敛至 WorkflowNodeMapper.xml。</p>
 */
public interface WorkflowNodeMapper {

  // ---------------- 节点配置 ----------------

  /** 项目是否存在（不存在或已归档时返回 0） */
  int countProject(@Param("projectId") Long projectId);

  /** 项目下的工作流节点（按 sort_order 升序） */
  List<LabelWorkflowNode> selectNodes(@Param("projectId") Long projectId);

  /** 项目下未删除节点数（0 表示尚未初始化） */
  int countNodes(@Param("projectId") Long projectId);

  /** 新增节点（同 projectId + nodeKey 幂等覆盖，含审核节点的批注类型） */
  int insertNode(LabelWorkflowNode node);

  /** 更新节点名称/顺序/说明/批注类型 */
  int updateNode(LabelWorkflowNode node);

  /**
   * 软删除项目下「不在保留列表内」的节点（保存时清理被移除的审核节点）。
   * keepKeys 为空时不执行删除，避免误删整个项目的配置。
   */
  int softDeleteNodesExcept(@Param("projectId") Long projectId,
                            @Param("keepKeys") List<String> keepKeys,
                            @Param("operator") Long operator);

  // ---------------- 任务当前节点 ----------------

  /** 任务所属项目ID */
  Long selectTaskProjectId(@Param("taskId") Long taskId);

  /** 任务当前所处节点 */
  String selectTaskCurrentNode(@Param("taskId") Long taskId);

  /** 任务名称 */
  String selectTaskName(@Param("taskId") Long taskId);

  /** 任务状态 */
  String selectTaskStatus(@Param("taskId") Long taskId);

  /** 任务置为已完成（全部审核节点通过后） */
  int updateTaskStatusCompleted(@Param("taskId") Long taskId, @Param("operator") Long operator);

  /**
   * 任务完成时同步完成数（条目级审核全部通过的完成路径）。
   * 与作业级验收的 {@code updateTaskCompletedCountByJob} 口径对齐，
   * 保证看板/项目进度按 completed_count 统计不失真。
   */
  int updateTaskCompletedCount(@Param("taskId") Long taskId,
                               @Param("completedCount") int completedCount,
                               @Param("operator") Long operator);

  /** 更新任务当前节点 */
  int updateTaskCurrentNode(@Param("taskId") Long taskId,
                            @Param("nodeKey") String nodeKey,
                            @Param("operator") Long operator);

  // ---------------- 任务条目流转统计 ----------------

  /** 任务下条目的流转统计（总数/标注阶段/已标注/已通过/待审核/已退回） */
  Map<String, Object> selectEntryFlowStats(@Param("taskId") Long taskId);

  /**
   * 推进到下一级审核节点：把已通过的条目重置为「未审核」，
   * 供下一级审核节点的成员重新领取审核（逐级独立审核）。
   */
  int resetEntryReviewForNextNode(@Param("taskId") Long taskId, @Param("operator") Long operator);

  /** 与 resetEntryReviewForNextNode 配套：把条目审核状态同步重置到包内数据资产。 */
  int syncAssetsReviewForNextNode(@Param("taskId") Long taskId, @Param("operator") Long operator);

  /** 全部审核节点通过后，把任务下的作业统一置为已完成（与任务终态保持一致）。 */
  int completeJobsByTask(@Param("taskId") Long taskId, @Param("operator") Long operator);
}
