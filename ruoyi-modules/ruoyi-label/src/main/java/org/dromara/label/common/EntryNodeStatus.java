package org.dromara.label.common;

/**
 * 数据条目节点状态常量（label_entry_node.status）。
 *
 * <p>所有工作流节点（标注 / 审核）统一使用：
 * 池子(pending)、待办(claimed)、已跳过(skipped)、已放弃(abandoned)、已完成(done)。</p>
 */
public final class EntryNodeStatus {

  private EntryNodeStatus() {
  }

  /** 池子：未领取，等待当前节点成员处理。 */
  public static final String PENDING = "pending";
  /** 待办：已被当前节点成员领取，正在处理中。 */
  public static final String CLAIMED = "claimed";
  /** 已跳过：当前节点成员主动跳过，需管理员/负责人恢复后重新进入池子。 */
  public static final String SKIPPED = "skipped";
  /** 已放弃：当前节点成员主动放弃，仅放弃人可恢复。 */
  public static final String ABANDONED = "abandoned";
  /** 已完成：当前节点处理结束（标注提交/审核通过/审核驳回）。 */
  public static final String DONE = "done";
}
