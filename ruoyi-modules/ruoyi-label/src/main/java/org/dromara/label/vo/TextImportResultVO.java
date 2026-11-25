package org.dromara.label.vo;

import java.io.Serializable;
import lombok.Data;

/**
 * 文本导入结果。
 *
 * <p>返回条目与资产 id，便于导入后直接跳转到该条目或继续后续操作。
 */
@Data
public class TextImportResultVO implements Serializable {

  /** 条目ID */
  private Long entryId;
  /** 资产ID */
  private Long assetId;
  /** 条目编号 */
  private String entryNo;
  /** 条目名称 */
  private String entryName;
  /** 文件名 */
  private String fileName;
  /** 字数 */
  private Integer charCount;
  /** 句数 */
  private Integer sentenceCount;
  /** 解析状态 */
  private String parseStatus;
}
