package org.dromara.label.common;

/**
 * 审核记录常量（label_review_record.review_type / result）。
 *
 * <p>审核记录类型区分初审/复审/验收；结果取值与 {@link LabelReviewStatus} 部分同值但语义为「本次动作结果」，
 * 故单独定义，避免混用。</p>
 */
public final class LabelReviewRecord {

  private LabelReviewRecord() {
  }

  /** 审核记录类型：初审。 */
  public static final String TYPE_REVIEW = "review";
  /** 审核记录类型：复审（第 2 轮起）。 */
  public static final String TYPE_RE_REVIEW = "re_review";
  /** 审核记录类型：验收。 */
  public static final String TYPE_ACCEPTANCE = "acceptance";
  /** 审核记录类型：审核员修订标注（审核节点直接保存标注，零状态副作用）。 */
  public static final String TYPE_REVIEWER_EDIT = "reviewer_edit";

  /** 审核结果：审核通过（作业进入待验收）。 */
  public static final String RESULT_APPROVED = "approved";
  /** 审核结果：驳回。 */
  public static final String RESULT_REJECTED = "rejected";
  /** 审核结果：验收通过。 */
  public static final String RESULT_ACCEPTED = "accepted";
  /** 审核结果：条目审核通过。 */
  public static final String RESULT_PASSED = "passed";
}
