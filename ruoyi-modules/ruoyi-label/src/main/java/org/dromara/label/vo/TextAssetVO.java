package org.dromara.label.vo;

import java.io.Serializable;
import lombok.Data;

/**
 * 作业内的文本条目（供标注页左侧的条目列表使用）。
 */
@Data
public class TextAssetVO implements Serializable {

  /** 资产ID（标注与保存用的标识） */
  private Long assetId;
  /** 条目ID */
  private Long entryId;
  /** 条目名称 */
  private String entryName;
  /** 文件名 */
  private String fileName;
  /** 条目标注状态 */
  private String labelStatus;
}
