package org.dromara.label.mapper;

import org.apache.ibatis.annotations.Param;
import org.dromara.label.vo.TaskMemberVO;

import java.util.List;

/**
 * 任务成员域数据访问（label_task_member / label_team_member / label_task_team / label_task）
 */
public interface TaskMemberMapper {

    /** 任务下发团队的全部成员（按用户去重，含「是否已配置为任务成员」标记） */
    List<TaskMemberVO> selectCandidates(@Param("taskId") Long taskId);

    /** 该用户是否已配置为任务成员（>=1 表示是） */
    int countMember(@Param("taskId") Long taskId, @Param("userId") Long userId);

    /** 任务已配置的成员总数（0 表示尚未配置，领取时回退为任务下发团队范围校验） */
    long countMembers(@Param("taskId") Long taskId);

    /** 该用户是否属于任务下发团队（含 label_task_team 多团队与 label_task.team_id 主团队） */
    int countTaskTeamMember(@Param("taskId") Long taskId, @Param("userId") Long userId);

    /** 任务下发团队的全部成员用户ID（任务管理员把条目分配给他人时的候选人范围） */
    List<Long> selectTaskTeamMemberUserIds(@Param("taskId") Long taskId);

    /** 任务创建人ID（任务创建者同样可配置任务成员） */
    Long selectCreatorId(@Param("taskId") Long taskId);

    /** 该用户在该任务内的角色（member/admin；不是任务成员时返回 null） */
    String selectMemberRole(@Param("taskId") Long taskId, @Param("userId") Long userId);

    /** 设置为任务成员（重复设置幂等，软删记录重新启用；nodeKey 为该成员归属的工作流节点） */
    int insertMember(@Param("taskId") Long taskId,
                     @Param("userId") Long userId,
                     @Param("nodeKey") String nodeKey,
                     @Param("operator") Long operator);

    /** 调整任务成员的任务内角色（仅已存在的任务成员记录可调整，返回影响行数） */
    int updateMemberRole(@Param("taskId") Long taskId,
                         @Param("userId") Long userId,
                         @Param("role") String role,
                         @Param("operator") Long operator);

    /** 调整任务成员归属的工作流节点（null 表示该用户不是任务成员） */
    String selectMemberNodeKey(@Param("taskId") Long taskId, @Param("userId") Long userId);

    /** 调整任务成员归属的工作流节点 */
    int updateMemberNode(@Param("taskId") Long taskId,
                         @Param("userId") Long userId,
                         @Param("nodeKey") String nodeKey,
                         @Param("operator") Long operator);
}
