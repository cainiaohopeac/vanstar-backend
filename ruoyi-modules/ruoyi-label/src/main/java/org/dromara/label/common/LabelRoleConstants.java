package org.dromara.label.common;

/**
 * 标注平台角色常量（对应 sys_role.role_key）。
 *
 * <p>业务规则：项目负责人只能从具有 {@link #PROJECT_LEADER} 角色的人员中选择；
 * 团队负责人只能从具有 {@link #TEAM_LEADER} 角色的人员中选择。</p>
 */
public final class LabelRoleConstants {

  /** 团队负责人：可被选为团队负责人。 */
  public static final String TEAM_LEADER = "team_leader";

  /** 项目负责人：可被选为项目负责人。 */
  public static final String PROJECT_LEADER = "project_leader";

  private LabelRoleConstants() {
  }
}
