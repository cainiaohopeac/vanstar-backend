package org.dromara.label.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.label.dto.TaskMemberAddDTO;
import org.dromara.label.dto.TaskMemberNodeDTO;
import org.dromara.label.dto.TaskMemberRoleDTO;
import org.dromara.label.mapper.TaskMapper;
import org.dromara.label.mapper.TaskMemberMapper;
import org.dromara.label.service.BaseService;
import org.dromara.label.service.TaskMemberService;
import org.dromara.label.service.WorkflowService;
import org.dromara.label.vo.TaskMemberConfigVO;
import org.dromara.label.vo.TaskMemberVO;
import org.dromara.label.vo.TaskVO;
import org.dromara.label.vo.WorkflowNodeVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 任务成员域服务实现：任务成员候选查询、成员与管理员配置、领取资格校验。
 * SQL 统一收敛至 TaskMemberMapper.xml。
 */
@Service
public class TaskMemberServiceImpl extends BaseService implements TaskMemberService {

  @Autowired
  private TaskMemberMapper taskMemberMapper;

  @Autowired
  private TaskMapper taskMapper;

  @Autowired
  private WorkflowService workflowService;

  @Override
  public List<TaskMemberVO> candidates(Long taskId) {
    if (taskMapper.selectTask(taskId) == null) {
      throw new ServiceException("任务不存在");
    }
    // 成员名单仅对有权配置任务成员的人开放（超管、任务创建者、团队负责人或任务管理员）
    if (!canConfig(taskId, currentUserId())) {
      throw new ServiceException("仅任务创建者、团队负责人或任务管理员可以查看任务成员配置");
    }
    List<TaskMemberVO> rows = taskMemberMapper.selectCandidates(taskId);
    return rows == null ? new ArrayList<>() : rows;
  }

  @Override
  public TaskMemberConfigVO config(Long taskId) {
    long userId = currentUserId();
    TaskMemberConfigVO vo = new TaskMemberConfigVO();
    vo.setCanConfig(canConfig(taskId, userId));
    vo.setAdmin(ROLE_ADMIN.equals(taskMemberMapper.selectMemberRole(taskId, userId)));
    return vo;
  }

  @Override
  public boolean isTaskAdmin(Long taskId, Long userId) {
    if (taskId == null || userId == null) {
      return false;
    }
    return ROLE_ADMIN.equals(taskMemberMapper.selectMemberRole(taskId, userId));
  }

  @Override
  public List<Long> taskTeamMemberUserIds(Long taskId) {
    if (taskId == null) {
      return new ArrayList<>();
    }
    List<Long> rows = taskMemberMapper.selectTaskTeamMemberUserIds(taskId);
    return rows == null ? new ArrayList<>() : rows;
  }

  @Override
  @Transactional
  public void addMember(Long taskId, TaskMemberAddDTO dto) {
    if (dto == null || dto.getUserId() == null) {
      throw new ServiceException("请选择要设置为任务成员的用户");
    }
    Long userId = dto.getUserId();
    long operator = currentUserId();
    TaskVO task = taskMapper.selectTask(taskId);
    if (task == null) {
      throw new ServiceException("任务不存在");
    }
    if (!canConfig(taskId, operator)) {
      throw new ServiceException("仅任务创建者、团队负责人或任务管理员可以配置任务成员");
    }
    // 只能从任务下发团队的成员中选择，避免把无关人员加入任务
    if (taskMemberMapper.countTaskTeamMember(taskId, userId) == 0) {
      throw new ServiceException("只能把该任务下发团队的成员设置为任务成员");
    }
    // 成员归属节点：留空默认归入「标注」节点（不允许为空）
    String nodeKey = dto.getNodeKey() == null || dto.getNodeKey().isBlank()
        ? WorkflowService.KEY_LABEL : dto.getNodeKey().trim();
    WorkflowNodeVO node = findNode(task.getProjectId(), nodeKey);
    if (node == null) {
      throw new ServiceException("所选工作流节点不存在，请刷新「工作流配置」后重试");
    }
    taskMemberMapper.insertMember(taskId, userId, nodeKey, operator);
    writeOperationLog("ADD_TASK_MEMBER", "task", taskId,
        Map.of("userId", userId, "nodeKey", nodeKey));
    createNotification(userId, "任务成员设置",
        "你已被设置为任务「" + task.getTaskName() + "」的成员，归属节点：" + node.getNodeName()
            + "，任务流转到该节点后即可领取", "task", taskId);
  }

  @Override
  @Transactional
  public void updateMemberNode(Long taskId, TaskMemberNodeDTO dto) {
    if (dto == null || dto.getUserId() == null) {
      throw new ServiceException("请选择要调整的任务成员");
    }
    String nodeKey = dto.getNodeKey() == null ? "" : dto.getNodeKey().trim();
    if (nodeKey.isEmpty()) {
      throw new ServiceException("请选择成员归属的工作流节点");
    }
    Long targetUserId = dto.getUserId();
    long operator = currentUserId();
    TaskVO task = taskMapper.selectTask(taskId);
    if (task == null) {
      throw new ServiceException("任务不存在");
    }
    if (!canConfig(taskId, operator)) {
      throw new ServiceException("仅任务创建者、团队负责人或任务管理员可以配置任务成员");
    }
    if (taskMemberMapper.countMember(taskId, targetUserId) == 0) {
      throw new ServiceException("该用户还不是任务成员，请先设置为任务成员");
    }
    WorkflowNodeVO node = findNode(task.getProjectId(), nodeKey);
    if (node == null) {
      throw new ServiceException("所选工作流节点不存在，请刷新「工作流配置」后重试");
    }
    if (taskMemberMapper.updateMemberNode(taskId, targetUserId, nodeKey, operator) <= 0) {
      throw new ServiceException("成员归属节点调整失败，请刷新后重试");
    }
    writeOperationLog("UPDATE_TASK_MEMBER_NODE", "task", taskId,
        Map.of("userId", targetUserId, "nodeKey", nodeKey));
    createNotification(targetUserId, "任务成员节点调整",
        "你在任务「" + task.getTaskName() + "」中的归属节点已调整为：" + node.getNodeName(), "task", taskId);
  }

  /** 项目工作流节点（按节点标识查找，不存在时返回 null）。 */
  private WorkflowNodeVO findNode(Long projectId, String nodeKey) {
    if (projectId == null || nodeKey == null) {
      return null;
    }
    List<WorkflowNodeVO> nodes = workflowService.nodes(projectId);
    for (WorkflowNodeVO node : nodes) {
      if (nodeKey.equals(node.getNodeKey())) {
        return node;
      }
    }
    return null;
  }

  @Override
  @Transactional
  public void updateMemberRole(Long taskId, TaskMemberRoleDTO dto) {
    if (dto == null || dto.getUserId() == null) {
      throw new ServiceException("请选择要调整的任务成员");
    }
    String role = dto.getRole() == null ? "" : dto.getRole().trim().toLowerCase();
    if (!ROLE_ADMIN.equals(role) && !ROLE_MEMBER.equals(role)) {
      throw new ServiceException("任务角色只能是普通成员或任务管理员");
    }
    Long targetUserId = dto.getUserId();
    long operator = currentUserId();
    TaskVO task = taskMapper.selectTask(taskId);
    if (task == null) {
      throw new ServiceException("任务不存在");
    }
    if (!canConfig(taskId, operator)) {
      throw new ServiceException("仅任务创建者、团队负责人或任务管理员可以配置任务成员");
    }
    // 只调整「已是任务成员」的用户角色，避免绕过成员配置流程直接获得任务身份
    if (taskMemberMapper.countMember(taskId, targetUserId) == 0) {
      throw new ServiceException("该用户还不是任务成员，请先设置为任务成员");
    }
    if (taskMemberMapper.updateMemberRole(taskId, targetUserId, role, operator) <= 0) {
      throw new ServiceException("任务角色调整失败，请刷新后重试");
    }
    writeOperationLog("UPDATE_TASK_MEMBER_ROLE", "task", taskId,
        Map.of("userId", targetUserId, "role", role));
    if (ROLE_ADMIN.equals(role)) {
      createNotification(targetUserId, "任务管理员设置",
          "你已被设置为任务「" + task.getTaskName() + "」的管理员，可以配置任务成员", "task", taskId);
    } else {
      createNotification(targetUserId, "任务管理员取消",
          "你在任务「" + task.getTaskName() + "」中的管理员身份已被取消", "task", taskId);
    }
  }

  @Override
  public boolean canClaim(Long taskId) {
    return canClaim(taskId, currentUserId());
  }

  /**
   * 领取资格：超管始终可领取；已配置任务成员时仅任务成员可领取；
   * 尚未配置任何成员时回退为「任务下发团队成员可领取」，避免存量任务无人可领取。
   */
  @Override
  public boolean canClaim(Long taskId, Long userId) {
    if (taskId == null || userId == null) {
      return false;
    }
    if (isSuperAdmin(userId)) {
      return true;
    }
    if (taskMemberMapper.countMembers(taskId) > 0) {
      return taskMemberMapper.countMember(taskId, userId) > 0;
    }
    return taskMemberMapper.countTaskTeamMember(taskId, userId) > 0;
  }

  @Override
  public boolean canClaim(Long taskId, Long userId, String nodeKey) {
    if (taskId == null || userId == null) {
      return false;
    }
    if (isSuperAdmin(userId)) {
      return true;
    }
    // 任务创建者 / 团队负责人 / 任务管理员不受节点限制
    Long creatorId = taskMemberMapper.selectCreatorId(taskId);
    if (creatorId != null && creatorId.equals(userId)) {
      return true;
    }
    if (taskMapper.countTaskTeamLeader(taskId, userId) > 0) {
      return true;
    }
    if (ROLE_ADMIN.equals(taskMemberMapper.selectMemberRole(taskId, userId))) {
      return true;
    }
    if (nodeKey == null || nodeKey.isBlank()) {
      return canClaim(taskId, userId);
    }
    if (taskMemberMapper.countMembers(taskId) > 0) {
      // 普通成员：只有任务流转到自己所属节点时才能领取
      String memberNode = taskMemberMapper.selectMemberNodeKey(taskId, userId);
      if (memberNode == null || memberNode.isBlank()) {
        return false;
      }
      return memberNode.equals(nodeKey.trim());
    }
    return taskMemberMapper.countTaskTeamMember(taskId, userId) > 0;
  }

  @Override
  public boolean canClaimAtNode(Long taskId, String nodeKey) {
    if (taskId == null) {
      return false;
    }
    if (nodeKey == null || nodeKey.isBlank()) {
      // 「领取标注」资格（不指定节点）：自己名下有待办（标注中/修订中/未修订，含被驳回修订）
      // 时始终可进入，不受任务节点流转限制；否则按任务当前有效节点校验
      // 这里只判断「是否有待办」用于按钮可用性，不限定批次（batchId 传 null）
      if (taskMapper.selectMyTodoEntry(taskId, currentUserId(), null) != null) {
        return true;
      }
      return canClaim(taskId, currentUserId(), workflowService.effectiveNodeKey(taskId));
    }
    return canClaim(taskId, currentUserId(), nodeKey.trim());
  }

  @Override
  public void assertClaimable(Long taskId, Long userId) {
    if (!canClaim(taskId, userId)) {
      throw new ServiceException("请先在「成员配置」中把自己设置为任务成员，再领取标注");
    }
  }

  @Override
  public void assertClaimable(Long taskId, Long userId, String nodeKey) {
    if (canClaim(taskId, userId, nodeKey)) {
      return;
    }
    if (taskMemberMapper.countMembers(taskId) > 0) {
      throw new ServiceException("当前任务还未流转到你所属的工作流节点，或你尚未被配置为该节点成员，暂时无法领取");
    }
    throw new ServiceException("请先在「成员配置」中把自己设置为任务成员，再领取");
  }

  /**
   * 是否有权配置该任务的成员：超管、任务创建者、任务下发团队的负责人或任务管理员
   * （任务管理员与团队负责人在任务内同级）。
   */
  private boolean canConfig(Long taskId, long operator) {
    if (isSuperAdmin(operator)) {
      return true;
    }
    Long creatorId = taskMemberMapper.selectCreatorId(taskId);
    if (creatorId != null && creatorId == operator) {
      return true;
    }
    if (taskMapper.countTaskTeamLeader(taskId, operator) > 0) {
      return true;
    }
    return ROLE_ADMIN.equals(taskMemberMapper.selectMemberRole(taskId, operator));
  }

  /** 是否超管（超管不受任务成员范围限制）。 */
  private boolean isSuperAdmin(long userId) {
    try {
      return LoginHelper.isSuperAdmin(userId);
    } catch (Exception e) {
      return false;
    }
  }
}
