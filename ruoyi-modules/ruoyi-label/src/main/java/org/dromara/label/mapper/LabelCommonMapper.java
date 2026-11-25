package org.dromara.label.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 通用支撑数据访问（原 BaseService 内公共 SQL 迁移至此）
 */
public interface LabelCommonMapper {

    /** 统计指定状态的作业数 */
    long countJobs(@Param("status") String status);

    /** 按表取下一编号（内部表名白名单调用） */
    long nextId(@Param("tableName") String tableName);

    /** 创建站内通知 */
    void insertNotification(@Param("userId") Long userId,
                            @Param("title") String title,
                            @Param("content") String content,
                            @Param("bizType") String bizType,
                            @Param("bizId") Long bizId);

    /** 按用户名/昵称查用户ID */
    Long findUserIdByName(@Param("name") String name);

    /** 按项目名查项目ID（未删除） */
    Long findProjectIdByName(@Param("projectName") String projectName);

    /** 按任务名查任务ID（未删除） */
    Long findTaskIdByName(@Param("taskName") String taskName);

    /** 查作业指派人ID */
    Long findAssigneeIdByJobId(@Param("jobId") Long jobId);

    /** 查作业审核人ID */
    Long findReviewerIdByJobId(@Param("jobId") Long jobId);

    /** 项目首个未删除类别集ID */
    Long firstClassSetId(@Param("projectId") Long projectId);

    /** 项目最近一个未删除批次ID */
    Long lastAssetBatchId(@Param("projectId") Long projectId);

    /** 插入自动上传批次 */
    int insertAssetBatch(@Param("projectId") Long projectId,
                         @Param("batchNo") String batchNo,
                         @Param("batchName") String batchName,
                         @Param("userId") Long userId);

    /** 按项目+批次号回查批次ID */
    Long selectBatchIdByNo(@Param("projectId") Long projectId, @Param("batchNo") String batchNo);

    /** 查批次所属项目ID（未删除），不存在返回 null */
    Long selectBatchProjectId(@Param("batchId") Long batchId);

    /** 批次状态置为已解析（序列导入等不改变资产计数的场景） */
    int touchAssetBatch(@Param("batchId") Long batchId);

    /** 作业的首个资产（job_asset 排序首位） */
    Long assetIdByJobAsset(@Param("jobId") Long jobId);

    /** 查作业所属项目ID */
    Long selectJobProjectId(@Param("jobId") Long jobId);

    /** 查作业数据类型 */
    String selectJobDataType(@Param("jobId") Long jobId);

    /** 项目内同媒体类型首个未删除资产 */
    Long firstAssetIdOfProject(@Param("projectId") Long projectId, @Param("mediaType") String mediaType);

    /** 按名查未删除团队ID */
    Long teamIdByName(@Param("teamName") String teamName);

    /** 创建团队 */
    int insertTeam(@Param("teamName") String teamName, @Param("userId") Long userId);

    /** 过滤出存在的未删除团队ID */
    List<Long> selectValidTeamIds(@Param("ids") List<Long> ids);

    /** 清空项目队伍关联 */
    int deleteProjectTeams(@Param("projectId") Long projectId);

    /** 写入项目队伍关联 */
    int insertProjectTeam(@Param("projectId") Long projectId, @Param("teamId") Long teamId,
                          @Param("userId") Long userId);

    /** 同步项目主队伍 */
    int updateProjectPrimaryTeam(@Param("projectId") Long projectId, @Param("teamId") Long teamId,
                                 @Param("userId") Long userId);

    /** 批量查项目队伍关联（含队名） */
    List<Map<String, Object>> selectProjectTeams(@Param("ids") List<Long> projectIds);

    /** 幂等写入团队成员 */
    int upsertTeamMember(@Param("teamId") Long teamId, @Param("userId") Long userId,
                         @Param("role") String role, @Param("operator") Long operator);

    /** 用户是否拥有指定角色（sys_role.role_key），返回命中条数 */
    int countUserRole(@Param("userId") Long userId, @Param("roleKey") String roleKey);
}
