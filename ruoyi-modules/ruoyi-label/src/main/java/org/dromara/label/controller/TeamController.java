package org.dromara.label.controller;

import java.util.List;
import org.dromara.label.common.PageResult;
import org.dromara.label.common.Result;
import org.dromara.label.dto.TeamMemberDTO;
import org.dromara.label.dto.TeamSaveDTO;
import org.dromara.label.service.TeamService;
import org.dromara.label.vo.TeamMemberVO;
import org.dromara.label.vo.TeamVO;
import org.dromara.label.vo.TeamWorkloadVO;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 团队域。 */
@RestController
public class TeamController {

  @Autowired
  private TeamService teamService;

  @GetMapping("/teams")
  public Result<PageResult<TeamVO>> teams() {
    return Result.success(teamService.teams());
  }

  @Log(title = "团队管理", businessType = BusinessType.INSERT)
  @PostMapping("/teams")
  public Result<TeamVO> createTeam(@RequestBody TeamSaveDTO dto) {
    return Result.success("创建成功", teamService.saveTeam(dto));
  }

  @GetMapping("/teams/{id}")
  public Result<TeamVO> team(@PathVariable Long id) {
    return Result.success(teamService.team(id));
  }

  @Log(title = "团队管理", businessType = BusinessType.UPDATE)
  @PutMapping("/teams/{id}")
  public Result<TeamVO> updateTeam(@PathVariable Long id, @RequestBody TeamSaveDTO dto) {
    dto.setId(id);
    return Result.success("保存成功", teamService.saveTeam(dto));
  }

  @Log(title = "团队管理", businessType = BusinessType.DELETE)
  @DeleteMapping("/teams/{id}")
  public Result<Void> deleteTeam(@PathVariable Long id) {
    teamService.deleteTeam(id);
    return Result.success("删除成功", null);
  }

  @GetMapping("/teams/{id}/members")
  public Result<List<TeamMemberVO>> teamMembers(@PathVariable Long id) {
    return Result.success(teamService.teamMembers(id));
  }

  @Log(title = "团队成员配置", businessType = BusinessType.UPDATE)
  @PutMapping("/teams/{id}/members")
  public Result<List<TeamMemberVO>> updateTeamMembers(
      @PathVariable Long id, @RequestBody List<TeamMemberDTO> members) {
    return Result.success("保存成功", teamService.saveTeamMembers(id, members));
  }

  @GetMapping("/teams/{id}/workload")
  public Result<List<TeamWorkloadVO>> teamWorkload(@PathVariable Long id) {
    return Result.success(teamService.teamWorkload(id));
  }
}
