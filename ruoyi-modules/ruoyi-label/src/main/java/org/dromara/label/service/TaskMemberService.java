package org.dromara.label.service;

import org.dromara.label.dto.TaskMemberAddDTO;
import org.dromara.label.dto.TaskMemberNodeDTO;
import org.dromara.label.dto.TaskMemberRoleDTO;
import org.dromara.label.vo.TaskMemberConfigVO;
import org.dromara.label.vo.TaskMemberVO;

import java.util.List;

/**
 * 任务成员域服务：维护任务的「可领取成员」及任务内角色（成员/管理员）。
 *
 * <p>任务下发到团队后，并非团队内每个人都能领取标注：需由团队负责人、任务管理员（或超管、任务创建者）
 * 在任务详情页通过「成员配置」把团队成员设置为任务成员，只有任务成员才能领取该任务的标注条目。</p>
 *
 * <p>任务管理员与团队负责人在任务内同级：同样可以打开「成员配置」继续配置成员与管理员；
 * 普通任务成员与非成员不展示该入口。</p>
 */
public interface TaskMemberService {

    /** 任务角色：普通任务成员（仅可领取标注）。 */
    String ROLE_MEMBER = "member";

    /** 任务角色：任务管理员（与团队负责人同级，可配置任务成员）。 */
    String ROLE_ADMIN = "admin";

    /**
     * 任务下发团队的全部成员（按用户去重，含「是否已配置为任务成员」与任务内角色标记），
     * 供「成员配置」弹窗展示。
     */
    List<TaskMemberVO> candidates(Long taskId);

    /** 当前登录用户是否有权配置该任务成员，以及是否为该任务管理员（决定「成员配置」入口是否展示）。 */
    TaskMemberConfigVO config(Long taskId);

    /** 指定用户是否为该任务的任务管理员。 */
    boolean isTaskAdmin(Long taskId, Long userId);

    /** 该任务下发团队的全部成员用户ID（任务管理员把条目分配给他人时的候选人范围）。 */
    List<Long> taskTeamMemberUserIds(Long taskId);

    /** 把团队中的某个用户设置为任务成员（仅超管、任务创建者、任务下发团队负责人或任务管理员）。 */
    void addMember(Long taskId, TaskMemberAddDTO dto);

    /** 调整任务成员的任务内角色（member=普通成员，admin=任务管理员）。 */
    void updateMemberRole(Long taskId, TaskMemberRoleDTO dto);

    /** 当前登录用户是否可领取该任务的标注。 */
    boolean canClaim(Long taskId);

    /** 指定用户是否可领取该任务的标注：已配置成员时仅成员可领取，未配置时任务下发团队成员可领取。 */
    boolean canClaim(Long taskId, Long userId);

    /**
     * 指定用户是否可在该任务的指定工作流节点领取。
     *
     * <p>超管、任务创建者、团队负责人、任务管理员不受节点限制；
     * 普通成员必须归属该节点（成员归属节点不允许为空，默认 label 标注节点），
     * 即：只有任务流转到自己所属节点时才能领取标注或审核。</p>
     */
    boolean canClaim(Long taskId, Long userId, String nodeKey);

    /**
     * 当前登录用户在任务「当前有效节点」或指定节点是否可领取。
     *
     * <p>nodeKey 为空（「领取标注」资格）时：自己名下有待办（含被驳回修订）直接可领取，
     * 否则按任务当前有效节点校验；指定 nodeKey 时仅按该节点校验。</p>
     */
    boolean canClaimAtNode(Long taskId, String nodeKey);

    /** 校验指定用户可领取该任务的标注，不可领取时抛出业务异常。 */
    void assertClaimable(Long taskId, Long userId);

    /** 校验指定用户可在指定节点领取，不可领取时抛出业务异常。 */
    void assertClaimable(Long taskId, Long userId, String nodeKey);

    /** 调整任务成员归属的工作流节点（标注节点 / 某个审核节点）。 */
    void updateMemberNode(Long taskId, TaskMemberNodeDTO dto);
}
