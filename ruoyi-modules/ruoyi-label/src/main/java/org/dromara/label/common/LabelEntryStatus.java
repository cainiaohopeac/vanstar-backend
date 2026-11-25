package org.dromara.label.common;

/**
 * 条目标注状态常量（label_data_entry.label_status）。
 *
 * <p>与字典 {@code label_entry_status} 一一对应，作为 Java 侧的唯一取值来源，避免魔法字符串散落。</p>
 */
public final class LabelEntryStatus {

  private LabelEntryStatus() {
  }

  /** 待分配（未指定标注员）。 */
  public static final String PENDING = "pending";
  /** 标注中（已领取/已分配）。 */
  public static final String LABELING = "labeling";
  /** 已标注（首次提交完成，待审核）。 */
  public static final String LABELED = "labeled";
  /** 未修订（审核不通过，待领取修订）。 */
  public static final String UNREVISED = "unrevised";
  /** 修订中（已领取修订）。 */
  public static final String REVISING = "revising";
  /** 已修订（修订后提交完成，待复审）。 */
  public static final String REVISED = "revised";
  /** 已跳过（标注中跳过，可由管理员重新分配）。 */
  public static final String SKIPPED = "skipped";
  /** 已放弃（标注员放弃当前条目；团队内可见，仅放弃人可恢复放弃前的标注状态）。 */
  public static final String ABANDONED = "abandoned";
}
