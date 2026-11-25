package org.dromara.label.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.dromara.label.common.PageResult;
import org.dromara.label.common.Result;
import org.dromara.label.dto.DashboardMyHandledDTO;
import org.dromara.label.dto.DashboardTeamHandledDTO;
import org.dromara.label.service.DashboardService;
import org.dromara.label.vo.DashboardSummaryVO;
import org.dromara.label.vo.DashboardTodoVO;
import org.dromara.label.vo.DashboardWorkloadVO;
import org.dromara.label.vo.MyHandledEntryVO;
import org.dromara.label.vo.ProjectProgressVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 看板统计域。 */
@RestController
public class DashboardController {

  @Autowired
  private DashboardService dashboardService;

  @GetMapping("/dashboard/summary")
  public Result<DashboardSummaryVO> summary() {
    return Result.success(dashboardService.dashboardSummary());
  }

  @GetMapping("/dashboard/todos")
  public Result<List<DashboardTodoVO>> todos() {
    return Result.success(dashboardService.dashboardTodos());
  }

  @GetMapping("/dashboard/project-progress")
  public Result<List<ProjectProgressVO>> projectProgress() {
    return Result.success(dashboardService.projectProgress());
  }

  /** 团队产能分页：pageNum 缺省 1，pageSize 缺省 10（服务端上限 100）。 */
  @GetMapping("/dashboard/workload")
  public Result<PageResult<DashboardWorkloadVO>> workload(
      @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
      @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
    return Result.success(dashboardService.workload(pageNum, pageSize));
  }

  /**
   * 「我的领取条目」分页：成员视角，跨任务/跨节点/跨批次聚合本人曾领取过的条目
   * （claimed/skipped/abandoned/done，排除 released）。
   */
  @PostMapping("/dashboard/my-handled-entries")
  public Result<PageResult<MyHandledEntryVO>> myHandledEntries(
      @RequestBody @Valid DashboardMyHandledDTO dto) {
    return Result.success(dashboardService.myHandledEntries(dto));
  }

  /**
   * 「团队领取条目」分页：负责人/管理员视角，限定到具体任务 + 批次，
   * 返回该任务该批次下所有已分配条目（不限 operator，不限节点）。
   * 仅团队负责人 / 任务管理员 / 超管可访问，service 端做权限校验。
   */
  @PostMapping("/dashboard/team-handled-entries")
  public Result<PageResult<MyHandledEntryVO>> teamHandledEntries(
      @RequestBody @Valid DashboardTeamHandledDTO dto) {
    return Result.success(dashboardService.teamHandledEntries(dto));
  }
}