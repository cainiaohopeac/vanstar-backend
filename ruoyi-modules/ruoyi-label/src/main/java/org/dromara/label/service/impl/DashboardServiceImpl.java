package org.dromara.label.service.impl;

import java.util.List;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.label.common.EntryNodeStatus;
import org.dromara.label.common.LabelJobStatus;
import org.dromara.label.common.PageResult;
import org.dromara.label.dto.DashboardMyHandledDTO;
import org.dromara.label.dto.DashboardTeamHandledDTO;
import org.dromara.label.mapper.DashboardMapper;
import org.dromara.label.mapper.TaskMapper;
import org.dromara.label.service.BaseService;
import org.dromara.label.service.DashboardService;
import org.dromara.label.service.TaskMemberService;
import org.dromara.label.vo.DashboardSummaryVO;
import org.dromara.label.vo.DashboardTodoVO;
import org.dromara.label.vo.DashboardWorkloadVO;
import org.dromara.label.vo.MyHandledEntryVO;
import org.dromara.label.vo.ProjectProgressVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 看板统计域服务实现：标注平台首页概览、待办、项目进度与人员工作量。
 * SQL 统一收敛至 DashboardMapper.xml。
 */
@Service
public class DashboardServiceImpl extends BaseService implements DashboardService {

  /** 单页最大条数，避免前端传入过大 pageSize 拖垮查询。 */
  private static final int MAX_PAGE_SIZE = 100;

  /** 「总览」默认包含的节点状态：释放后 operator_id 已置 NULL，天然不命中。 */
  private static final List<String> DEFAULT_HANDLED_STATUSES = List.of(
      EntryNodeStatus.CLAIMED,
      EntryNodeStatus.SKIPPED,
      EntryNodeStatus.ABANDONED,
      EntryNodeStatus.DONE);

  @Autowired
  private DashboardMapper dashboardMapper;

  @Autowired
  private TaskMapper taskMapper;

  @Autowired
  private TaskMemberService taskMemberService;

  @Override
  public DashboardSummaryVO dashboardSummary() {
    return dashboardMapper.selectDashboardSummary();
  }

  @Override
  public List<DashboardTodoVO> dashboardTodos() {
    return List.of(
        new DashboardTodoVO(
            LabelJobStatus.PENDING_LABEL,
            "待标注作业",
            countJobs(LabelJobStatus.PENDING_LABEL),
            "/label-platform/work"),
        new DashboardTodoVO(
            LabelJobStatus.PENDING_REVIEW,
            "待审核作业",
            countJobs(LabelJobStatus.PENDING_REVIEW),
            "/label-platform/reviews"),
        new DashboardTodoVO(
            LabelJobStatus.REVIEW_REJECTED,
            "退回返修",
            countJobs(LabelJobStatus.REVIEW_REJECTED),
            "/label-platform/tasks?status=" + LabelJobStatus.REVIEW_REJECTED),
        new DashboardTodoVO(
            LabelJobStatus.PENDING_ACCEPTANCE,
            "待验收",
            countJobs(LabelJobStatus.PENDING_ACCEPTANCE),
            "/label-platform/reviews?status=" + LabelJobStatus.PENDING_ACCEPTANCE));
  }

  @Override
  public List<ProjectProgressVO> projectProgress() {
    return dashboardMapper.selectProjectProgress();
  }

  @Override
  public PageResult<DashboardWorkloadVO> workload(int pageNum, int pageSize) {
    int page = Math.max(1, pageNum);
    int size = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);
    int offset = (page - 1) * size;
    long total = dashboardMapper.countWorkload();
    List<DashboardWorkloadVO> list = dashboardMapper.selectWorkload(offset, size);
    return new PageResult<>(total, list);
  }

  @Override
  public PageResult<MyHandledEntryVO> myHandledEntries(DashboardMyHandledDTO dto) {
    long userId = currentUserId();
    int page = dto.getPageNum() == null || dto.getPageNum() < 1 ? 1 : dto.getPageNum();
    int size = dto.getPageSize() == null || dto.getPageSize() < 1 ? 20 : dto.getPageSize();
    size = Math.min(size, MAX_PAGE_SIZE);
    int offset = (page - 1) * size;
    List<String> statuses = (dto.getStatusList() == null || dto.getStatusList().isEmpty())
        ? DEFAULT_HANDLED_STATUSES : dto.getStatusList();
    long total = dashboardMapper.countMyHandledEntries(userId,
        dto.getTaskId(), dto.getBatchId(), statuses, dto.getKeyword());
    List<MyHandledEntryVO> list = dashboardMapper.selectMyHandledEntries(userId,
        dto.getTaskId(), dto.getBatchId(), statuses, dto.getKeyword(), offset, size);
    return new PageResult<>(total, list);
  }

  @Override
  public PageResult<MyHandledEntryVO> teamHandledEntries(DashboardTeamHandledDTO dto) {
    long userId = currentUserId();
    assertTeamManagerAuthority(userId, dto.getTaskId());
    int page = dto.getPageNum() == null || dto.getPageNum() < 1 ? 1 : dto.getPageNum();
    int size = dto.getPageSize() == null || dto.getPageSize() < 1 ? 20 : dto.getPageSize();
    size = Math.min(size, MAX_PAGE_SIZE);
    int offset = (page - 1) * size;
    List<String> statuses = (dto.getStatusList() == null || dto.getStatusList().isEmpty())
        ? DEFAULT_HANDLED_STATUSES : dto.getStatusList();
    long total = dashboardMapper.countTeamHandledEntries(dto.getTaskId(), dto.getBatchId(),
        statuses, dto.getKeyword());
    List<MyHandledEntryVO> list = dashboardMapper.selectTeamHandledEntries(dto.getTaskId(),
        dto.getBatchId(), statuses, dto.getKeyword(), offset, size);
    return new PageResult<>(total, list);
  }

  /**
   * 校验当前用户是否对该任务拥有「团队负责人 / 任务管理员 / 超管」身份——团队总览的权限闸门。
   * 不满足时抛出 ServiceException 阻断请求。
   */
  private void assertTeamManagerAuthority(long userId, Long taskId) {
    if (isSuperAdmin(userId)) {
      return;
    }
    if (taskMapper.countTaskTeamLeader(taskId, userId) > 0) {
      return;
    }
    if (taskMemberService.isTaskAdmin(taskId, userId)) {
      return;
    }
    throw new ServiceException("仅团队负责人 / 任务管理员 / 超管可查看该任务的团队领取条目");
  }

  private boolean isSuperAdmin(long userId) {
    try {
      return LoginHelper.isSuperAdmin(userId);
    } catch (Exception e) {
      return false;
    }
  }
}