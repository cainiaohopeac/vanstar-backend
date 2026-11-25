package org.dromara.label.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.label.common.LabelRoleConstants;
import org.dromara.label.common.PageResult;
import org.dromara.label.dto.TeamMemberDTO;
import org.dromara.label.dto.TeamSaveDTO;
import org.dromara.label.mapper.TeamMapper;
import org.dromara.label.service.BaseService;
import org.dromara.label.service.TeamService;
import org.dromara.label.vo.TeamMemberVO;
import org.dromara.label.vo.TeamVO;
import org.dromara.label.vo.TeamWorkloadVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 团队域服务：团队列表/详情/新建/编辑/删除、团队成员维护、团队工作量统计。
 * SQL 统一收敛至 TeamMapper.xml。
 */
@Service
public class TeamServiceImpl extends BaseService implements TeamService {

  @Autowired
  private TeamMapper teamMapper;

  /** 团队列表：含成员数、进行中任务数、周产出与质检合格率汇总。 */
  @Override
  public PageResult<TeamVO> teams() {
    return PageResult.of(teamMapper.selectTeams());
  }

  @Override
  public TeamVO team(Long id) {
    TeamVO team = teamMapper.selectTeam(id);
    if (team == null) {
      throw new ServiceException("团队不存在");
    }
    return team;
  }

  /** 新建/编辑团队：负责人自动成为团队成员(leader)。 */
  @Override
  @Transactional
  public TeamVO saveTeam(TeamSaveDTO dto) {
    Long id = dto.getId();
    String teamName = dto.getTeamName() == null || dto.getTeamName().isBlank() ? "新团队" : dto.getTeamName();
    Long leaderId = findUserId(dto.getLeaderName(), currentUserId());
    // 新建团队：团队负责人只能从具有「团队负责人」角色的人员中选择
    if (id == null && dto.getLeaderName() != null && !dto.getLeaderName().isBlank()) {
      assertUserHasRole(leaderId, LabelRoleConstants.TEAM_LEADER, "团队负责人");
    }
    String status = dto.getStatus() == null || dto.getStatus().isBlank() ? "active" : dto.getStatus();
    long userId = currentUserId();
    if (id == null) {
      Map<String, Object> p = new HashMap<>();
      p.put("teamName", teamName);
      p.put("leaderId", leaderId);
      p.put("status", status);
      p.put("userId", userId);
      teamMapper.insertTeam(p);
      id = asLong(p.get("id"));
      ensureTeamMember(id, leaderId, "leader");
      writeOperationLog("CREATE_TEAM", "team", id, Map.of("teamName", teamName));
      return team(id);
    }
    Map<String, Object> p = new HashMap<>();
    p.put("id", id);
    p.put("teamName", teamName);
    p.put("leaderId", leaderId);
    p.put("status", status);
    p.put("userId", userId);
    teamMapper.updateTeam(p);
    ensureTeamMember(id, leaderId, "leader");
    writeOperationLog("UPDATE_TEAM", "team", id, Map.of("teamName", teamName));
    return team(id);
  }

  /** 删除团队：先校验无项目绑定（主队伍或多队关联）。 */
  @Override
  @Transactional
  public void deleteTeam(Long id) {
    long boundProjects = teamMapper.countTeamBoundProjects(id);
    if (boundProjects > 0) {
      throw new ServiceException(
          String.format("该队伍已被 %d 个项目绑定，无法删除，请先解绑相关项目", boundProjects));
    }
    teamMapper.softDeleteTeam(id, currentUserId());
    writeOperationLog("DELETE_TEAM", "team", id, Map.of("teamId", id));
  }

  @Override
  public List<TeamMemberVO> teamMembers(Long teamId) {
    return teamMapper.selectTeamMembers(teamId);
  }

  /** 保存团队成员：全量替换（先物理删除旧成员再写入新成员），并强制保证团队负责人在成员列表中且角色为 leader。 */
  @Override
  @Transactional
  public List<TeamMemberVO> saveTeamMembers(Long teamId, List<TeamMemberDTO> members) {
    Long leaderId = teamMapper.selectTeamLeaderId(teamId);
    teamMapper.deleteTeamMembersByTeam(teamId);
    if (members != null) {
      for (TeamMemberDTO member : members) {
        if (member.getUserId() == null) {
          continue;
        }
        String role = member.getTeamRole() == null || member.getTeamRole().isBlank()
            ? "annotator" : member.getTeamRole();
        Map<String, Object> p = new HashMap<>();
        p.put("teamId", teamId);
        p.put("userId", member.getUserId());
        p.put("role", role);
        p.put("actorId", currentUserId());
        teamMapper.insertTeamMember(p);
      }
    }
    if (leaderId != null) {
      ensureTeamMember(teamId, leaderId, "leader");
    }
    writeOperationLog(
        "SAVE_TEAM_MEMBERS",
        "team",
        teamId,
        Map.of("memberCount", members == null ? 0 : members.size()));
    return teamMembers(teamId);
  }

  /** 团队工作量：成员今日/本周/累计标注量与审核量。 */
  @Override
  public List<TeamWorkloadVO> teamWorkload(Long teamId) {
    return teamMapper.selectTeamWorkload(teamId);
  }
}
