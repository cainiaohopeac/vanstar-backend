package org.dromara.label.mapper;

import org.apache.ibatis.annotations.Param;
import org.dromara.label.vo.TeamMemberVO;
import org.dromara.label.vo.TeamVO;
import org.dromara.label.vo.TeamWorkloadVO;

import java.util.List;
import java.util.Map;

/**
 * 团队域数据访问（label_team/label_team_member/label_project_team/label_task/label_qc_report）
 */
public interface TeamMapper {

    List<TeamVO> selectTeams();

    TeamVO selectTeam(@Param("id") Long id);

    Long selectTeamLeaderId(@Param("id") Long id);

    /** 统计团队被多少个未删除项目绑定（主队伍或多队关联）。 */
    long countTeamBoundProjects(@Param("teamId") Long teamId);

    int insertTeam(Map<String, Object> p);

    int updateTeam(Map<String, Object> p);

    int softDeleteTeam(@Param("id") Long id, @Param("userId") Long userId);

    // ---------------- 团队成员 ----------------

    List<TeamMemberVO> selectTeamMembers(@Param("teamId") Long teamId);

    int deleteTeamMembersByTeam(@Param("teamId") Long teamId);

    int insertTeamMember(Map<String, Object> p);

    // ---------------- 工作量 ----------------

    List<TeamWorkloadVO> selectTeamWorkload(@Param("teamId") Long teamId);
}
