package org.dromara.label.common;

/**
 * 条目操作历史 - 操作类型标识常量。
 */
public final class LabelEntryHistoryAction {

  private LabelEntryHistoryAction() {
  }

  /** 分配 */
  public static final String ASSIGN = "assign";
  /** 领取标注 */
  public static final String CLAIM = "claim";
  /** 领取修订 */
  public static final String CLAIM_REVISE = "claim_revise";
  /** 提交标注 */
  public static final String SUBMIT = "submit";
  /** 提交修订 */
  public static final String SUBMIT_REVISE = "submit_revise";
  /** 领取审核 */
  public static final String REVIEW_CLAIM = "review_claim";
  /** 审核通过 */
  public static final String REVIEW_PASS = "review_pass";
  /** 审核驳回 */
  public static final String REVIEW_REJECT = "review_reject";
  /** 跳过 */
  public static final String SKIP = "skip";
  /** 放弃 */
  public static final String ABANDON = "abandon";
  /** 恢复 */
  public static final String RESTORE = "restore";
  /** 释放 / 移出 */
  public static final String RELEASE = "release";
}
