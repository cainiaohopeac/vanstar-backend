package org.dromara.label.common;

/**
 * 作业/任务状态常量（label_job.status / label_task.status）。
 *
 * <p>与字典 {@code label_task_status} 一一对应，作为 Java 侧的唯一取值来源，避免魔法字符串散落。</p>
 */
public final class LabelJobStatus {

  private LabelJobStatus() {
  }

  /** 待分配（任务已下发，尚未分配/领取条目）。 */
  public static final String PENDING_ASSIGN = "pending_assign";
  /** 待标注（作业已生成，尚无人开始标注）。 */
  public static final String PENDING_LABEL = "pending_label";
  /** 标注中（标注/修订进行中）。 */
  public static final String LABELING = "labeling";
  /** 待审核（作业已提交审核）。 */
  public static final String PENDING_REVIEW = "pending_review";
  /** 已退回（审核驳回，需返修）。 */
  public static final String REVIEW_REJECTED = "review_rejected";
  /** 待验收（审核通过，等待验收）。 */
  public static final String PENDING_ACCEPTANCE = "pending_acceptance";
  /** 已完成（验收通过/任务终态）。 */
  public static final String COMPLETED = "completed";
}
