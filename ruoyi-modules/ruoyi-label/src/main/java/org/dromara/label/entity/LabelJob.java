package org.dromara.label.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 标注作业（label_job）在文本链路用到的字段：提交审核后推进作业状态与进度。
 */
@Data
public class LabelJob implements Serializable {

  /** 作业ID */
  private Long id;
  /** 作业编号 */
  private String jobNo;
  /** 项目ID */
  private Long projectId;
  /** 数据类型 */
  private String dataType;
  /** 作业状态 */
  private String status;
  /** 进度（0-100） */
  private Integer progress;
  /** 当前审核人 */
  private Long reviewerId;
  /** 创建人 */
  private Long createBy;
  /** 更新人 */
  private Long updateBy;
  /** 创建时间 */
  private LocalDateTime createTime;
  /** 更新时间 */
  private LocalDateTime updateTime;
  /** 删除标记 */
  private Integer deleted;
}
