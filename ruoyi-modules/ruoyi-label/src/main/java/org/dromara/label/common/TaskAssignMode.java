package org.dromara.label.common;

/**
 * 任务条目分配方式常量（任务分配接口的 mode 参数）。
 */
public final class TaskAssignMode {

  /** 自行领取（未指定标注员时按领取处理） */
  public static final String CLAIM = "claim";

  /** 管理员指派（指定标注员） */
  public static final String ASSIGN = "assign";

  private TaskAssignMode() {}
}
