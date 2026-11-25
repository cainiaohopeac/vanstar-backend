package org.dromara.label.vo;

import java.io.Serializable;
import lombok.Data;

/**
 * 作业的条目完成进度：总数与已完成数。
 *
 * <p>用于判断作业内条目是否已全部提交，从而决定作业进入「待审核」还是仍在「标注中」。
 */
@Data
public class JobEntryProgressVO implements Serializable {

  /** 条目的资产总数 */
  private Long total;
  /** 已完成（已标注/已修订/已跳过/已放弃）的条目数 */
  private Long done;
}
