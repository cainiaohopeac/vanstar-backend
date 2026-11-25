package org.dromara.label.service;

import java.util.List;
import org.dromara.label.common.PageResult;
import org.dromara.label.dto.EntryAssignDTO;
import org.dromara.label.dto.TaskEntryListQueryDTO;
import org.dromara.label.dto.TaskOverviewEntriesDTO;
import org.dromara.label.dto.TaskQueryDTO;
import org.dromara.label.dto.JobAssignDTO;
import org.dromara.label.dto.JobStatusDTO;
import org.dromara.label.dto.TaskSaveDTO;
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

/**
 * 任务域服务：任务创建/编辑/拆分/发布/收藏、作业(job)列表与详情、作业分配、
 * 任务进度聚合刷新。
 */
public interface TaskService {

  // ---------------- 任务 ----------------

  /**
   * 分页查询任务列表（查询条件以 TaskQueryDTO 请求体传递）。
   *
   * <p>scope=manage：任务管理，仅展示「自己创建」的任务；
   * scope=received：数据任务列表，展示「本人负责团队收到的任务」与「本人已被配置为任务成员的任务」的并集；
   * scope 为空：兼容旧调用（团队负责人限定本团队，其他角色可见全部）。</p>
   */
  PageResult<TaskVO> tasks(TaskQueryDTO dto);

  /** 查询任务详情。 */
  TaskVO task(Long id);

  /** 将任务拆分为作业。 */
  TaskSplitVO splitTask(Long id);

  /** 发布任务（派发到可领取池）。 */
  TaskPublishVO publishTask(Long id);

  /** 任务批次分页（仅任务下发的批次，含条目数 / 待分配数；scope=myteam 按团队范围、scope=mine 仅本人已领取范围过滤）。 */
  PageResult<AssetBatchVO> taskBatches(Long taskId, String scope);

  /** 任务明细分页。 */
  PageResult<AssetVO> taskItems(Long taskId);

  // ---------------- 任务数据条目（条目级分配） ----------------

  /**
   * 任务条目分页（按批次 / 页签标签 / 关键字过滤；scope=myteam 只看本团队认领及待分配条目，scope=mine 只看本人已领取条目）。
   * nodeKey 为空或 label 时保持原标标注节点逻辑；传入 review 等节点时按节点状态展示池子/待办/跳过/放弃/完成。
   */
  PageResult<TaskEntryVO> entries(TaskEntryListQueryDTO dto);

  /** 任务条目状态统计（总览/待分配/已跳过/未修订/已放弃 页签计数；scope=myteam 按团队范围、scope=mine 按本人范围过滤；batchId 必填）。 */
  TaskEntrySummaryVO entrySummary(Long taskId, Long batchId, String scope, String nodeKey);

  /**
   * 任务详情「总览」分页：跨节点聚合该 task+batch 下已被领取/处理的条目。
   * 团队负责人/任务管理员/超管看全部（operator_id IS NOT NULL）；
   * 普通成员仅看本人当前持有且未释放的（operator_id = me）。
   * 不受 nodeKey 限制——总览 tab 应一次性呈现所有节点数据。
   */
  PageResult<TaskOverviewEntryVO> overviewEntries(TaskOverviewEntriesDTO dto);

  /** 任务详情「总览」4 种状态分布统计（不计 pending）。 */
  TaskOverviewEntriesSummaryVO overviewEntriesSummary(Long taskId, Long batchId);

  /** 条目级分配：领取标注（mode=claim）或分配给他人（mode=assign，仅团队负责人或任务管理员）。 */
  TaskVO assignEntries(Long taskId, EntryAssignDTO dto);

  /**
   * 自动领取标注（「领取标注」未勾选条目时）：
   * 本人有待办时返回第一条待办——被驳回的「未修订」条目最优先，领取即置为「修订中」（source=revise）；
   * 其次是进行中的「标注中/修订中」条目（source=todo）；
   * 无待办时在任务池内随机领取一条「待分配且无人领取」的条目给当前用户（source=random）。
   * 返回条目与对应作业ID，前端据此直接进入标注工作台。
   */
  TaskClaimAutoVO claimAutoEntry(Long taskId, Long batchId);

  /**
   * 批量查询条目归属作业ID（任务详情「查看」多选/全选时按作业分组打开只读工作台）。
   * 无作业承载（尚未领取）的条目不在返回结果中。
   */
  List<EntryJobVO> entryJobIds(Long taskId, List<Long> entryIds);

  /** 标注员跳过：把「标注中/修订中」的选中条目置为已跳过，等待管理员重新分配。 */
  TaskVO skipEntries(Long taskId, EntryAssignDTO dto);

  /** 领取修订：把「未修订」的选中条目录取为自己修订，进入修订中。 */
  TaskVO claimReviseEntries(Long taskId, EntryAssignDTO dto);

  /**
   * 标注员放弃：把「标注中/修订中」且在自己名下的选中条目置为「已放弃」。
   * 已放弃条目对团队全体成员可见，且只有放弃人本人可以恢复。
   */
  TaskVO abandonEntries(Long taskId, EntryAssignDTO dto);

  /** 恢复已放弃条目：仅放弃人本人可恢复，条目标注状态回滚到放弃前状态。 */
  TaskVO restoreEntries(Long taskId, EntryAssignDTO dto);

  /**
   * 释放领取（仅团队负责人/任务管理员/超管）：把已领取、进行中的条目退回未领取，
   * 状态自动回退（标注中→待分配、修订中→未修订、审核中/复审中→未审核），节点记录回池子。
   */
  TaskVO releaseEntries(Long taskId, EntryAssignDTO dto);

  /** 审核驳回后把相关作业退回标注（作业状态置为已退回），便于标注员重新修订。 */
  void markEntriesReturned(List<Long> assetIds);

  /** 定位条目所属作业，供任务详情页「查看 / 预览标注」跳转。 */
  Long entryJobId(Long taskId, Long assetId);

  /** 当前用户负责的团队名称（空表示不是团队负责人，可见全部任务）。 */
  List<String> myLeaderTeams();

  /** 当前用户负责的团队ID（空表示不是团队负责人）。 */
  List<Long> myLeaderTeamIds();

  /** 批量分配作业批次。 */
  TaskBatchAssignVO assignBatch(Long taskId, Long batchId);

  /** 任务可追加的批次（同项目下、未下发到任何任务、且尚未在本任务中）。 */
  List<AssetBatchVO> appendableBatches(Long taskId);

  /** 追加批次：把新批次并入任务并展开作业/资产，任务继续标注（沿用 createTask+splitTask 的批次处理路径）。 */
  TaskVO appendBatches(Long taskId, TaskSaveDTO dto);

  /** 创建任务：项目须为进行中，同批次不可重复下发；写入任务及团队/批次下发范围。 */
  TaskVO createTask(TaskSaveDTO dto);

  /** 更新任务：按 dto.id 更新任务基本信息，并同步团队/批次下发范围（id 必传）。 */
  TaskVO updateTask(TaskSaveDTO dto);

  /** 项目下已被任务下发的批次（同一批次只允许下发一次，供前端禁用已下发选项）。 */
  List<BatchOccupancyVO> batchOccupancy(Long projectId);

  /** 取任务所属项目 ID（用于标注页反查项目级标注文档）；任务不存在返回 null。 */
  Long projectIdOfTask(Long taskId);

  // ---------------- 作业 ----------------

  /** 任务下的作业列表。 */
  List<JobVO> jobs(Long taskId);

  /**
   * 按条件过滤作业列表（供 myJobs / reviews 下推 SQL，避免全量加载内存过滤）。
   * assigneeId 非空时按标注员过滤；statuses 为放行状态集合；status 为精确状态（可空）。
   */
  List<JobVO> jobsByFilter(Long assigneeId, List<String> statuses, String status);

  /** 作业详情。 */
  JobVO job(Long id);

  /** 分配作业给标注/审核人员。 */
  JobVO assignJob(Long id, JobAssignDTO dto);

  /** 更新作业状态（领取/提交/开始等）。 */
  JobVO updateJobStatus(Long id, JobStatusDTO dto);

  /** 根据作业完成情况同步任务进度与状态。 */
  void syncTaskStatus(Long taskId);
}
