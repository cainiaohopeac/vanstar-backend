package org.dromara.label.common;

/**
 * 任务列表查询范围常量（前端任务页签 / 后端 tasks 接口的 scope 参数）。
 *
 * <p>决定任务列表按创建人、团队或本人维度过滤。</p>
 */
public final class TaskScope {

  /** 任务管理（谁创建的谁可见） */
  public static final String MANAGE = "manage";

  /** 我收到的（下发给本人团队、或本人被配置为任务成员的任务） */
  public static final String RECEIVED = "received";

  /** 我的团队（团队负责人限定本团队） */
  public static final String MYTEAM = "myteam";

  /** 我的（仅本人相关的数据） */
  public static final String MINE = "mine";

  private TaskScope() {}
}
