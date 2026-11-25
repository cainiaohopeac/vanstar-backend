package org.dromara.label.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 条目操作历史展示 VO（任务详情「条目历史」弹窗用）。
 */
@Data
public class EntryHistoryVO implements Serializable {

  private static final long serialVersionUID = 1L;

  private Long id;
  private Long entryId;
  private Long taskId;
  private Long batchId;
  /** 操作类型标识 */
  private String action;
  /** 操作中文名 */
  private String actionName;
  /** 操作节点/阶段 */
  private String nodeName;
  /** 操作详情 */
  private String detail;
  private Long operatorId;
  /** 操作人显示名（昵称优先，回退用户名） */
  private String operatorName;
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  private LocalDateTime createTime;
}
