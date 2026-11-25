package org.dromara.label.common;

/**
 * 条目审核状态常量（label_asset.review_status）。
 *
 * <p>与字典 {@code label_review_status} 一一对应；审核状态与标注状态相互独立，
 * 审核通过不修改标注状态，仅把审核状态置为 {@link #PASSED}。</p>
 */
public final class LabelReviewStatus {

  private LabelReviewStatus() {
  }

  /** 未审核（尚无人领取）。 */
  public static final String NONE = "none";
  /** 审核中（初审已领取）。 */
  public static final String REVIEWING = "reviewing";
  /** 审核不通过（初审驳回）。 */
  public static final String REJECTED = "rejected";
  /** 复审中（复审已领取，第 2 轮起）。 */
  public static final String RE_REVIEWING = "re_reviewing";
  /** 复审不通过（复审驳回）。 */
  public static final String RE_REJECTED = "re_rejected";
  /** 已通过（终态：初审或复审通过）。 */
  public static final String PASSED = "passed";
}
