package org.dromara.label.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.dromara.label.common.Result;
import org.dromara.label.dto.TaskMemberAddDTO;
import org.dromara.label.dto.TaskMemberNodeDTO;
import org.dromara.label.dto.TaskMemberRoleDTO;
import org.dromara.label.service.TaskMemberService;
import org.dromara.label.vo.TaskMemberConfigVO;
import org.dromara.label.vo.TaskMemberVO;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 任务成员域：任务成员与任务管理员配置（成员配置后才能领取该任务的标注）。 */
@RestController
public class TaskMemberController {

  @Autowired
  private TaskMemberService taskMemberService;

  /** 任务下发团队的全部成员（含是否已配置为任务成员及任务内角色），供任务详情页「成员配置」弹窗展示。 */
  @GetMapping("/tasks/{id}/members/candidates")
  public Result<List<TaskMemberVO>> candidates(@PathVariable Long id) {
    return Result.success(taskMemberService.candidates(id));
  }

  /** 当前用户是否有权配置该任务成员、是否为该任务管理员（决定「成员配置」入口是否展示）。 */
  @GetMapping("/tasks/{id}/members/configurable")
  public Result<TaskMemberConfigVO> configurable(@PathVariable Long id) {
    return Result.success(taskMemberService.config(id));
  }

  /**
   * 当前用户是否可在该任务的工作流节点领取（用于前端按钮可用性判断，后端领取时仍会二次校验）。
   * 不传 nodeKey 时按任务「当前有效节点」判断；成员只有在任务流转到自己所属节点时才能领取。
   */
  @GetMapping("/tasks/{id}/members/claimable")
  public Result<Boolean> claimable(@PathVariable Long id,
      @RequestParam(required = false) String nodeKey) {
    return Result.success(taskMemberService.canClaimAtNode(id, nodeKey));
  }

  /** 把团队中的某个成员设置为任务成员（仅超管、任务创建者、团队负责人或任务管理员）。 */
  @Log(title = "任务成员配置", businessType = BusinessType.INSERT)
  @PostMapping("/tasks/{id}/members")
  public Result<Void> addMember(@PathVariable Long id, @Valid @RequestBody TaskMemberAddDTO dto) {
    taskMemberService.addMember(id, dto);
    return Result.success("已设置为任务成员", null);
  }

  /** 调整任务成员的任务内角色（把「已是成员」的用户设为任务管理员或恢复为普通成员）。 */
  @Log(title = "任务成员角色调整", businessType = BusinessType.UPDATE)
  @PutMapping("/tasks/{id}/members/role")
  public Result<Void> updateMemberRole(@PathVariable Long id,
      @Valid @RequestBody TaskMemberRoleDTO dto) {
    taskMemberService.updateMemberRole(id, dto);
    return Result.success("任务角色已更新", null);
  }

  /** 调整任务成员归属的工作流节点（标注节点 / 某个审核节点），决定该成员在哪个节点可领取。 */
  @Log(title = "任务成员节点调整", businessType = BusinessType.UPDATE)
  @PutMapping("/tasks/{id}/members/node")
  public Result<Void> updateMemberNode(@PathVariable Long id,
      @Valid @RequestBody TaskMemberNodeDTO dto) {
    taskMemberService.updateMemberNode(id, dto);
    return Result.success("成员归属节点已更新", null);
  }
}
