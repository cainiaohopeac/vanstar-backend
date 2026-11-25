package org.dromara.label.service;

import java.util.List;
import org.dromara.label.common.PageResult;
import org.dromara.label.dto.TeamMemberDTO;
import org.dromara.label.dto.TeamSaveDTO;
import org.dromara.label.vo.TeamMemberVO;
import org.dromara.label.vo.TeamVO;
import org.dromara.label.vo.TeamWorkloadVO;

/**
 * 团队域服务：团队列表/详情/新建/编辑/删除、团队成员维护、团队工作量统计。
 */
public interface TeamService {

  /** 团队列表：含成员数、进行中任务数、周产出与质检合格率汇总。 */
  PageResult<TeamVO> teams();

  /** 团队详情。 */
  TeamVO team(Long id);

  /** 创建或更新团队。 */
  TeamVO saveTeam(TeamSaveDTO dto);

  /** 删除团队。 */
  void deleteTeam(Long id);

  /** 团队成员列表。 */
  List<TeamMemberVO> teamMembers(Long teamId);

  /** 保存团队成员（全量覆盖）。 */
  List<TeamMemberVO> saveTeamMembers(Long teamId, List<TeamMemberDTO> members);

  /** 团队工作量统计。 */
  List<TeamWorkloadVO> teamWorkload(Long teamId);
}
