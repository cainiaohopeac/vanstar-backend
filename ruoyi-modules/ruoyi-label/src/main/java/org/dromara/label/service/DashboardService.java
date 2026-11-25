package org.dromara.label.service;

import java.util.List;
import org.dromara.label.common.PageResult;
import org.dromara.label.dto.DashboardMyHandledDTO;
import org.dromara.label.dto.DashboardTeamHandledDTO;
import org.dromara.label.vo.DashboardSummaryVO;
import org.dromara.label.vo.DashboardTodoVO;
import org.dromara.label.vo.DashboardWorkloadVO;
import org.dromara.label.vo.MyHandledEntryVO;
import org.dromara.label.vo.ProjectProgressVO;

/**
 * 看板统计域服务：标注平台首页概览、待办事项、项目进度与人员工作量。
 */
public interface DashboardService {

    /** 首页汇总统计（项目/资产/作业/审核/质检等计数与完成率）。 */
    DashboardSummaryVO dashboardSummary();

    /** 待办事项统计（待标注/待审核/退回返修/待验收）。 */
    List<DashboardTodoVO> dashboardTodos();

    /** 项目任务进度列表。 */
    List<ProjectProgressVO> projectProgress();

    /** 人员工作量分页（总量/今日/本周/审核量）。 */
    PageResult<DashboardWorkloadVO> workload(int pageNum, int pageSize);

    /**
     * 「我的领取条目」分页：成员视角，跨任务/跨节点/跨批次聚合本人曾领取过的条目
     * （claimed/skipped/abandoned/done，排除 released）。与负责人/管理员视角分离。
     */
    PageResult<MyHandledEntryVO> myHandledEntries(DashboardMyHandledDTO dto);

    /**
     * 「团队领取条目」分页：负责人/管理员视角，限定到具体任务 + 批次，
     * 返回该任务该批次下所有已分配条目（不限 operator，不限节点）。
     * 仅团队负责人 / 任务管理员 / 超管可访问，service 端做权限校验。
     */
    PageResult<MyHandledEntryVO> teamHandledEntries(DashboardTeamHandledDTO dto);
}