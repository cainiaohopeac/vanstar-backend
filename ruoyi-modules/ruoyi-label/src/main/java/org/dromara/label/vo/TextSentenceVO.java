package org.dromara.label.vo;

import java.io.Serializable;
import lombok.Data;

/**
 * 文本句边界（由 label_asset.meta_json 的 sentences 还原）。
 *
 * <p>前端据此完成：按句分页渲染（长文本不可能整篇塞进 DOM）、
 * 句级情感标注定位、审核时的「句子跳转」。
 * 偏移同样是全文级，前端按 {@code start}/{@code end} 从全文中切片即可。
 */
@Data
public class TextSentenceVO implements Serializable {

  /** 句序号（从 0 开始） */
  private Integer index;
  /** 起始字符偏移（含） */
  private Integer start;
  /** 结束字符偏移（不含） */
  private Integer end;
}
