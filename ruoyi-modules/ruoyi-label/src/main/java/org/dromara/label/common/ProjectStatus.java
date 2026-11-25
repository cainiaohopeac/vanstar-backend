package org.dromara.label.common;

/**
 * 项目状态常量（label_project.status）。
 *
 * <p>项目生命周期：待启动 → 进行中 → 已完成 → 已归档（归档即软删，可恢复回已完成）。</p>
 */
public final class ProjectStatus {

  /** 待启动（新建初始状态，配置团队/标签/数据后方可启动） */
  public static final String CREATED = "created";

  /** 进行中（已启动，可下发任务） */
  public static final String RUNNING = "running";

  /** 已完成（终态之一，可归档） */
  public static final String COMPLETED = "completed";

  /** 已归档（软删除视图，可恢复） */
  public static final String ARCHIVED = "archived";

  private ProjectStatus() {}
}
