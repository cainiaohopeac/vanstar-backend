package org.dromara.label.mapper;

import org.apache.ibatis.annotations.Param;
import java.util.List;
import org.dromara.label.entity.LabelTask;
import org.dromara.label.vo.AssetBatchVO;
import org.dromara.label.vo.BatchOccupancyVO;
import org.dromara.label.vo.EntryJobVO;
import org.dromara.label.vo.JobVO;
import org.dromara.label.vo.TaskEntrySummaryVO;
import org.dromara.label.vo.TaskEntryVO;
import org.dromara.label.vo.TaskOverviewEntriesSummaryVO;
import org.dromara.label.vo.TaskOverviewEntryVO;
import org.dromara.label.vo.TaskVO;

import java.util.List;
import java.util.Map;

/**
 * 任务域数据访问（label_task/label_job/label_job_asset 等）
 */
public interface TaskMapper {

    // ---------------- 任务 ----------------

    /**
     * 任务列表查询。
     *
     * @param taskNo       非空时任务号模糊匹配
     * @param taskName     非空时任务名称模糊匹配
     * @param projectName  非空时项目名称模糊匹配
     * @param creatorId    非空时只查该创建人的任务（任务管理视图）
     * @param teamIds      非空时只查下发给这些团队的任务（团队负责人视角）
     * @param memberUserId 非空时额外并入「该用户已被配置为任务成员」的任务，与 teamIds 取并集
     */
    List<TaskVO> selectTasks(@Param("taskNo") String taskNo,
                             @Param("taskName") String taskName,
                             @Param("projectName") String projectName,
                             @Param("status") String status,
                             @Param("teamName") String teamName,
                             @Param("creatorId") Long creatorId,
                             @Param("teamIds") List<Long> teamIds,
                             @Param("memberUserId") Long memberUserId);

    TaskVO selectTask(@Param("id") Long id);

    /** 当前用户作为负责人（label_team.leader_id 或 label_team_member.team_role=leader）的团队ID */
    List<Long> selectLeaderTeamIds(@Param("userId") Long userId);

    /** 任务是否开启预标注引入（标注工作台取数时判断是否注入预标注框） */
    Integer selectTaskPreAnnotationEnabled(@Param("taskId") Long taskId);


    /** 当前用户负责的团队名称（用于任务列表「我的团队任务」提示） */
    List<String> selectLeaderTeamNames(@Param("userId") Long userId);

    /** 当前用户是否为该任务下发团队的负责人（>=1 表示是） */
    int countTaskTeamLeader(@Param("taskId") Long taskId, @Param("userId") Long userId);

    Long selectTaskProjectId(@Param("id") Long id);

    /** 任务的数据类型（image / point_cloud / mixed），用于领取条目时按任务类型归作业。 */
    String selectTaskDataType(@Param("id") Long id);

    Long selectLatestBatchId(@Param("projectId") Long projectId);

    // ---------------- 任务下发范围（团队/批次 多对多） ----------------

    int deleteTaskTeams(@Param("taskId") Long taskId);

    int insertTaskTeam(@Param("taskId") Long taskId,
                       @Param("teamId") Long teamId,
                       @Param("userId") Long userId);

    List<Long> selectTaskTeamIds(@Param("taskId") Long taskId);

    List<Map<String, Object>> selectTaskTeams(@Param("ids") List<Long> taskIds);

    int deleteTaskBatches(@Param("taskId") Long taskId);

    int insertTaskBatch(@Param("taskId") Long taskId,
                        @Param("batchId") Long batchId,
                        @Param("userId") Long userId);

    /** 批量下发批次关联：一条 INSERT 写入全部 batchId，替代逐条 insertTaskBatch 的 N 次往返。 */
    int insertTaskBatches(@Param("taskId") Long taskId,
                          @Param("batchIds") List<Long> batchIds,
                          @Param("userId") Long userId);

    /** 批次调整时只删除被移出的关联，避免「全删再全插」的无效写入。 */
    int deleteTaskBatchesByIds(@Param("taskId") Long taskId,
                               @Param("batchIds") List<Long> batchIds);

    List<Long> selectTaskBatchIds(@Param("taskId") Long taskId);

    /**
     * 任务可追加的批次：同项目下、未下发到任何任务、且尚未在本任务中的批次（供补充批次弹窗候选）。
     */
    List<AssetBatchVO> selectAppendableBatches(@Param("projectId") Long projectId, @Param("taskId") Long taskId);

    /** 更新任务资产总数（追加批次后累加）。 */
    int updateTaskAssetCount(@Param("taskId") Long taskId,
                             @Param("assetCount") int assetCount,
                             @Param("userId") Long userId);

    List<Map<String, Object>> selectTaskBatches(@Param("ids") List<Long> taskIds);

    /** 项目下已被任务下发的批次（同一批次只允许下发一次，供前端禁用已下发选项） */
    List<BatchOccupancyVO> selectBatchOccupancy(@Param("projectId") Long projectId);

    /** 校验所选批次是否已被其它任务占用（excludeTaskId 为当前任务，编辑时排除自身） */
    List<BatchOccupancyVO> selectOccupiedBatches(@Param("batchIds") List<Long> batchIds,
                                                 @Param("excludeTaskId") Long excludeTaskId);

    int insertTask(LabelTask task);

    int updateTask(LabelTask task);

    int updateTaskStatus(@Param("taskId") Long taskId,
                         @Param("status") String status,
                         @Param("userId") Long userId);

    int updateTaskAfterSplit(@Param("taskId") Long taskId,
                             @Param("jobCount") long jobCount,
                             @Param("userId") Long userId);

    // ---------------- 作业 ----------------

    List<JobVO> selectJobs(@Param("taskId") Long taskId);

    /**
     * 按处理人 + 状态集合过滤作业（myJobs / reviews 专用，把内存过滤下推到 SQL）。
     * assigneeId 非空时按标注员过滤；statuses 为放行状态集合；status 为精确状态（可空）。
     */
    List<JobVO> selectJobsByFilter(@Param("assigneeId") Long assigneeId,
                                   @Param("statuses") List<String> statuses,
                                   @Param("status") String status);

    JobVO selectJob(@Param("id") Long id);

    Long selectJobTaskId(@Param("id") Long id);

    int countJobsByTask(@Param("taskId") Long taskId);

    int countAssetsByProjectAndType(@Param("projectId") Long projectId,
                                    @Param("mediaType") String mediaType);

    /** 指定批次内某媒体类型的资产数 */
    int countAssetsByBatchAndType(@Param("batchId") Long batchId,
                                  @Param("mediaType") String mediaType);

    /** 指定批次集合内的资产总数 */
    int countAssetsByBatches(@Param("batchIds") List<Long> batchIds);

    int insertJob(Map<String, Object> p);

    int insertJobAssets(@Param("jobId") Long jobId,
                        @Param("projectId") Long projectId,
                        @Param("mediaType") String mediaType);

    /** 仅把指定批次内某媒体类型资产挂到作业下（任务按批次下发时使用） */
    int insertJobAssetsByBatch(@Param("jobId") Long jobId,
                               @Param("batchId") Long batchId,
                               @Param("mediaType") String mediaType);

    int updateJobAssignee(@Param("id") Long id,
                          @Param("assigneeId") Long assigneeId,
                          @Param("reviewerId") Long reviewerId,
                          @Param("userId") Long userId);

    int updateJobStatus(@Param("id") Long id,
                        @Param("status") String status,
                        @Param("userId") Long userId);

    List<String> selectJobStatuses(@Param("taskId") Long taskId);

    // ---------------- 任务数据条目（条目级分配） ----------------

    /** 任务下发的数据批次（含条目数 / 待分配数统计）；scopeUserIds 非空时按团队范围、mineUserId 非空时仅看本人已领取范围过滤 */
    List<AssetBatchVO> selectTaskBatchesWithCount(@Param("taskId") Long taskId,
                                                  @Param("scopeUserIds") List<Long> scopeUserIds,
                                                  @Param("mineUserId") Long mineUserId);

    long countEntries(@Param("taskId") Long taskId,
                      @Param("batchId") Long batchId,
                      @Param("tab") String tab,
                      @Param("keyword") String keyword,
                      @Param("scopeUserIds") List<Long> scopeUserIds,
                      @Param("mineUserId") Long mineUserId);

    List<TaskEntryVO> selectEntries(@Param("taskId") Long taskId,
                                    @Param("batchId") Long batchId,
                                    @Param("tab") String tab,
                                    @Param("keyword") String keyword,
                                    @Param("scopeUserIds") List<Long> scopeUserIds,
                                    @Param("mineUserId") Long mineUserId,
                                    @Param("offset") int offset,
                                    @Param("limit") int limit);

    TaskEntrySummaryVO selectEntrySummary(@Param("taskId") Long taskId,
                                          @Param("batchId") Long batchId,
                                          @Param("scopeUserIds") List<Long> scopeUserIds,
                                          @Param("mineUserId") Long mineUserId);

    // ---------------- 按工作流节点查询条目（任务详情页节点切换） ----------------

    long countEntriesByNode(@Param("taskId") Long taskId,
                            @Param("batchId") Long batchId,
                            @Param("tab") String tab,
                            @Param("keyword") String keyword,
                            @Param("nodeKey") String nodeKey,
                            @Param("userId") Long userId,
                            @Param("scope") String scope);

    List<TaskEntryVO> selectEntriesByNode(@Param("taskId") Long taskId,
                                          @Param("batchId") Long batchId,
                                          @Param("tab") String tab,
                                          @Param("keyword") String keyword,
                                          @Param("nodeKey") String nodeKey,
                                          @Param("userId") Long userId,
                                          @Param("scope") String scope,
                                          @Param("offset") int offset,
                                          @Param("limit") int limit);

    TaskEntrySummaryVO selectEntrySummaryByNode(@Param("taskId") Long taskId,
                                                @Param("batchId") Long batchId,
                                                @Param("nodeKey") String nodeKey,
                                                @Param("scope") String scope,
                                                @Param("userId") Long userId);

    // ---------------- 任务详情「总览」（跨节点聚合，不受 nodeKey 限制） ----------------

    /**
         * 「总览」分页：该 task+batch 下的条目（跨节点聚合，不限节点类型）。
         * 可见性按角色分叉（service 判定后传参）：scope=mine + userId 时仅返回本人参与过的条目
         * （任一节点记录 operator_id = 本人）；团队负责人 / 任务管理员 / 超管传 scope=null，返回全部条目。
         * 投影只取列表页实际展示与行内判定所需字段：数据类型取 label_task.data_type（不回查 label_asset），
         * 标注团队为任务级聚合，节点字段取最新一条节点记录。
         */
        List<TaskOverviewEntryVO> selectOverviewEntries(@Param("taskId") Long taskId,
                                                        @Param("batchId") Long batchId,
                                                        @Param("statusList") List<String> statusList,
                                                        @Param("keyword") String keyword,
                                                        @Param("scope") String scope,
                                                        @Param("userId") Long userId,
                                                        @Param("offset") int offset,
                                                        @Param("limit") int limit);

        /** 与 selectOverviewEntries 同口径计数（含相同的角色可见性分叉）。 */
        long countOverviewEntries(@Param("taskId") Long taskId,
                                  @Param("batchId") Long batchId,
                                  @Param("statusList") List<String> statusList,
                                  @Param("keyword") String keyword,
                                  @Param("scope") String scope,
                                  @Param("userId") Long userId);

        /** 「总览」状态分布统计（含 pending，含相同的角色可见性分叉）。 */
        TaskOverviewEntriesSummaryVO selectOverviewEntriesSummary(@Param("taskId") Long taskId,
                                                                  @Param("batchId") Long batchId,
                                                                  @Param("scope") String scope,
                                                                  @Param("userId") Long userId);

    /** 本人在该任务下的第一条待办条目（已领取且未完成：标注中/修订中/未修订，按批次与序号排序）。 */
    TaskEntryVO selectMyTodoEntry(@Param("taskId") Long taskId, @Param("userId") Long userId,
                                  @Param("batchId") Long batchId);

    /** 审核节点自动领取定位：该节点池子中第一条可领取（已标注/已修订且未审核）的条目。 */
    TaskEntryVO selectFirstPendingNodeEntry(@Param("taskId") Long taskId, @Param("nodeKey") String nodeKey);

    /**
     * 节点池子兜底：任意 pending 第一条（不限 label_status / review_status）。
     * 用于"待审核"池子（已标/已修 + 未审核）已空时，仍允许审核员领取先审后标。
     */
    TaskEntryVO selectAnyFirstPendingNodeEntry(@Param("taskId") Long taskId, @Param("nodeKey") String nodeKey);

    /**
     * 节点视图领取按钮专用：本人在该任务该节点下已领取（status='claimed' 且 operator_id=我）的第一条，
     * 按 n.claim_time ASC 升序排（先领取的先审，FIFO 处理积压）。
     * 不受当前 tab / scope 限制——按钮的语义就是「我名下已领取的待办入口」；
     * 若当前 tab 切到了别的页签（待办 / 已审核 / 已跳过 / 总览），
     * 也能通过这个 query 直接定位到我名下最早领取的待审核条目，避免白点。
     */
    TaskEntryVO selectMyFirstClaimedNodeEntry(@Param("taskId") Long taskId,
                                              @Param("userId") Long userId,
                                              @Param("nodeKey") String nodeKey);

    /** 随机挑选一条可领取条目（待分配且无人领取；配合 updateEntryClaim 的条件更新防并发重复分配）。 */
    TaskEntryVO selectRandomPendingEntry(@Param("taskId") Long taskId, @Param("batchId") Long batchId);

    /** 挑选一条可接管修订的条目（未修订优先于修订中，不限当前归属；配合 updateEntryTakeRevise 防并发）。 */
    TaskEntryVO selectRandomRevisableEntry(@Param("taskId") Long taskId, @Param("batchId") Long batchId);

    /** 团队内的成员用户ID（用于「我的团队」条目范围过滤） */
    List<Long> selectTeamMemberUserIds(@Param("teamIds") List<Long> teamIds);

    /** 条目范围：把数据条目展开为包内数据资产，并给出批次与数据类型（分配时用于归入对应作业） */
    List<Map<String, Object>> selectAssetScope(@Param("assetIds") List<Long> entryIds);

    int updateEntryAssign(@Param("assetIds") List<Long> entryIds,
                          @Param("assigneeId") Long assigneeId,
                          @Param("reviewerId") Long reviewerId,
                          @Param("labelStatus") String labelStatus,
                          @Param("userId") Long userId);

    /**
     * 领取标注：仅「待分配且尚未指定标注员」的条目可被领取，已被领取的条目不会被重复领取。
     *
     * @return 实际更新的条目数（小于入参数量即说明存在已被领取的条目）
     */
    int updateEntryClaim(@Param("assetIds") List<Long> entryIds,
                         @Param("assigneeId") Long assigneeId,
                         @Param("reviewerId") Long reviewerId,
                         @Param("userId") Long userId);

    /** 条目状态快照（跳过/领取修订前校验选中条目的当前状态与归属）。 */
    List<Map<String, Object>> selectEntryStatuses(@Param("assetIds") List<Long> entryIds);

    /** 批量查询条目归属作业ID（任务详情「查看」多选时按作业分组打开只读工作台）；无作业的条目不返回 */
    List<EntryJobVO> selectEntryJobIds(@Param("taskId") Long taskId, @Param("entryIds") List<Long> entryIds);

    /**
     * 标注员跳过：仅「标注中/修订中」的条目可跳过，进入已跳过并记录跳过人；
     * superAdmin 为 true 时不限制条目归属（运维兜底），否则仅能跳过自己名下的条目。
     *
     * @return 实际更新的条目数（小于入参数量即说明存在状态或归属不满足的条目）
     */
    int updateEntrySkip(@Param("assetIds") List<Long> entryIds,
                        @Param("userId") Long userId,
                        @Param("superAdmin") boolean superAdmin);

    /** 释放领取：已领取、进行中的条目退回未领取（标注中→待分配、修订中→未修订、审核中/复审中→未审核），返回释放条数。 */
    int updateEntryRelease(@Param("assetIds") List<Long> entryIds,
                           @Param("userId") Long userId);

    /**
     * 标注员放弃条目：仅「标注中/修订中」的条目可放弃，进入已放弃并记录放弃人；
     * superAdmin 为 true 时不限制条目归属（运维兜底），否则仅能放弃自己名下的条目。
     *
     * @return 实际更新的条目数（小于入参数量即说明存在状态或归属不满足的条目）
     */
    int updateEntryAbandon(@Param("assetIds") List<Long> entryIds,
                           @Param("userId") Long userId,
                           @Param("superAdmin") boolean superAdmin);

    /**
     * 恢复已放弃条目：仅放弃人本人可恢复，标注状态回滚到放弃前状态。
     *
     * @return 实际更新的条目数（小于入参数量即说明存在非本人放弃的条目）
     */
    int updateEntryRestore(@Param("assetIds") List<Long> entryIds, @Param("userId") Long userId);

    /**
     * 恢复已跳过条目：标注状态回滚到跳过前状态（缺省回退为 labeling），
     * 跳过人/时间/前状态清空；归属（assignee_id）保留为跳过人本人以便继续标注。
     * 权限判定在业务层完成（本人/团队负责人/任务管理员），本 SQL 不再校验 skip_by。
     *
     * @return 实际更新的条目数
     */
    int updateEntryRestoreSkipped(@Param("assetIds") List<Long> entryIds, @Param("userId") Long userId);

    /** 领取修订：把「未修订」的条目领取为自己修订，进入修订中。 */
    int updateEntryClaimRevise(@Param("assetIds") List<Long> entryIds, @Param("userId") Long userId);

    /**
     * 接管修订：把「未修订/修订中」条目（含他人名下）的修订人置为当前用户，统一进入修订中。
     *
     * @return 实际更新的条目数（小于入参数量即说明存在状态已流转的条目）
     */
    int updateEntryTakeRevise(@Param("assetIds") List<Long> entryIds, @Param("userId") Long userId);

    /**
     * 领取标注/领取修订/接管修订后，把 entry × task × label 节点的 claimed 记录落库，
     * 供总览 / 节点列表透出节点状态与操作人（总览不依赖节点记录是否存在，仅用其拼显示字段）。
     * 走 uk_entry_node(entry_id, node_key) 唯一键 ON DUPLICATE KEY UPDATE，重复领取原地更新。
     * 与 updateEntryClaim / updateEntryClaimRevise / updateEntryTakeRevise 同事务调用，回滚行为一致。
     *
     * @param taskId     任务 ID
     * @param entryIds   已被领取/接管的条目 ID 列表（与 updateEntry* 共用同一份）
     * @param operatorId 领取人 = 节点操作人（assignee_id）
     * @param userId     当前操作用户（写入 created_by/updated_by）
     * @return 实际落库的节点行数（含 INSERT IGNORE 命中的行）
     */
    int insertEntryNodeClaim(@Param("taskId") Long taskId,
                             @Param("entryIds") List<Long> entryIds,
                             @Param("operatorId") Long operatorId,
                             @Param("userId") Long userId);

    /** 条目级流转后，把条目的流转状态同步到包内全部数据资产（工作台/审核按数据资产工作）。 */
    int syncAssetsFromEntries(@Param("assetIds") List<Long> entryIds);

    /** 按条目回写作业(job)状态（审核驳回→已退回；领取修订→标注中），便于标注员重新修订。 */
    int updateJobStatusByAssetIds(@Param("assetIds") List<Long> entryIds,
                                  @Param("status") String status,
                                  @Param("userId") Long userId);

    /** 把条目从该任务下所有作业中摘除（重新分配前清理归属） */
    int deleteJobAssetsByTaskAndAssets(@Param("taskId") Long taskId,
                                       @Param("assetIds") List<Long> entryIds);

    /** 条目当前挂载的作业ID（放弃条目时摘除数据后刷新作业范围） */
    List<Long> selectJobIdsByTaskAndEntries(@Param("taskId") Long taskId,
                                            @Param("assetIds") List<Long> entryIds);

    /** 任务下仍处于「已放弃」的条目数（任务状态聚合时排除「已完成」误判） */
    long countAbandonedEntries(@Param("taskId") Long taskId);

    /** 任务下发/调整批次后，把批次内的数据条目绑定到任务。 */
    int bindEntriesToTask(@Param("taskId") Long taskId,
                          @Param("batchIds") List<Long> batchIds,
                          @Param("userId") Long userId);

    /**
     * 任务调整批次移出后，解绑这些批次下仍挂在本任务的条目（task_id 置空）。
     * 与 bindEntriesToTask 成对维护 task_id 这一唯一归属来源；条件带 task_id 过滤，
     * 不会误伤已被其它任务接手的条目。
     */
    int unbindEntriesFromTask(@Param("taskId") Long taskId,
                              @Param("batchIds") List<Long> batchIds,
                              @Param("userId") Long userId);

    int insertJobAssetsByAssetIds(@Param("jobId") Long jobId,
                                  @Param("assetIds") List<Long> assetIds);

    Long selectJobIdByScope(@Param("taskId") Long taskId,
                            @Param("batchId") Long batchId,
                            @Param("dataType") String dataType,
                            @Param("assigneeId") Long assigneeId);

    /** 依据作业实际挂载条目数刷新条目数与状态 */
    int refreshJobScopeMeta(@Param("jobId") Long jobId, @Param("userId") Long userId);

    /** 软删除任务下已无条目的作业 */
    int softDeleteEmptyJobsByTask(@Param("taskId") Long taskId, @Param("userId") Long userId);

    /** 定位条目所属（或应归属）的作业，供任务详情页「查看 / 预览标注」跳转 */
    Long selectJobIdByAssetForTask(@Param("taskId") Long taskId, @Param("assetId") Long assetId);
}
