package org.dromara.label.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.dromara.label.vo.DashboardSummaryVO;
import org.dromara.label.vo.DashboardWorkloadVO;
import org.dromara.label.vo.MyHandledEntryVO;
import org.dromara.label.vo.ProjectProgressVO;

/**
 * 看板统计域数据访问
 */
public interface DashboardMapper {

    DashboardSummaryVO selectDashboardSummary();

    List<ProjectProgressVO> selectProjectProgress();

    /** 人员工作量分页查询。 */
    List<DashboardWorkloadVO> selectWorkload(@Param("offset") int offset, @Param("limit") int limit);

    /** 参与统计的在岗人员总数（分页 total）。 */
    long countWorkload();

    /**
     * 「我的领取条目」分页：成员视角，限定到具体 task + batch，返回该任务该批次下本人
     * 当前持有且未释放的所有节点条目（不限节点）。
     */
    List<MyHandledEntryVO> selectMyHandledEntries(@Param("userId") Long userId,
                                                  @Param("taskId") Long taskId,
                                                  @Param("batchId") Long batchId,
                                                  @Param("statusList") List<String> statusList,
                                                  @Param("keyword") String keyword,
                                                  @Param("offset") int offset,
                                                  @Param("limit") int limit);

    long countMyHandledEntries(@Param("userId") Long userId,
                               @Param("taskId") Long taskId,
                               @Param("batchId") Long batchId,
                               @Param("statusList") List<String> statusList,
                               @Param("keyword") String keyword);

    /**
     * 「团队领取条目」分页：负责人/管理员视角，限定到具体 task + batch，
     * 返回该任务该批次下所有已分配条目（不限 operator，不限节点）。
     * 仅团队负责人 / 任务管理员 / 超管可访问，由 service 端做权限校验。
     */
    List<MyHandledEntryVO> selectTeamHandledEntries(@Param("taskId") Long taskId,
                                                    @Param("batchId") Long batchId,
                                                    @Param("statusList") List<String> statusList,
                                                    @Param("keyword") String keyword,
                                                    @Param("offset") int offset,
                                                    @Param("limit") int limit);

    long countTeamHandledEntries(@Param("taskId") Long taskId,
                                 @Param("batchId") Long batchId,
                                 @Param("statusList") List<String> statusList,
                                 @Param("keyword") String keyword);
}