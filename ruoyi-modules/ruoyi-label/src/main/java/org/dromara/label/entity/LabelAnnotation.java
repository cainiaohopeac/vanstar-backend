package org.dromara.label.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 标注存档（label_annotation）。
 *
 * <p>文本标注与视觉标注共用这张表，一行即「某作业下某资产的一份存档」。
 * 这里只声明文本链路用到的字段；视觉链路仍走 AnnotationMapper 的 Map 版方法，
 * 两套互不干扰（MyBatis 允许不同 mapper 操作同一张表）。
 */
@Data
public class LabelAnnotation implements Serializable {

  /** 标注ID */
  private Long id;
  /** 作业ID */
  private Long jobId;
  /** 数据资产ID */
  private Long assetId;
  /** 标注员ID */
  private Long annotatorId;
  /** 标注状态（draft / submitted） */
  private String status;
  /** 存档 JSON（文本标注存批注与条目级结论） */
  private String annotationJson;
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
