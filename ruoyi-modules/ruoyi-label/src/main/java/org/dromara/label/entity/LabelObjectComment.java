package org.dromara.label.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 标注对象级批注（label_object_comment）：作业 + 资产维度的框级/条目级批注。
 *
 * <p>objectKey 非空表示实例（框级）批注；objectKey 为空且 pos* 有值表示条目级位置批注。
 * commentType 为审核节点配置的错误内容（漏点/漏标/不贴合/混点/类别错误或自定义类型），
 * content 为审核员填写的描述（不超过 200 字）。</p>
 */
@Data
public class LabelObjectComment implements Serializable {

  /** 批注ID */
  private Long id;
  /** 所属作业ID */
  private Long jobId;
  /** 资产（图片/点云帧）ID */
  private Long assetId;
  /** 标注对象 key（为空表示条目级批注） */
  private String objectKey;
  /** 产生环节：label 标注 / review 审核 */
  private String node;
  /** 批注描述（≤200 字） */
  private String content;
  /** 批注类型（错误内容，来自审核节点配置；标注环节批注可为空） */
  private String commentType;
  /** 3D 位置坐标：条目级位置批注使用；实例批注为空 */
  private Double posX;
  private Double posY;
  private Double posZ;
  /** 状态：open 待处理 / corrected 已修正 */
  private String status;
  private Long createdBy;
  private Long updatedBy;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
  /** 逻辑删除标记 */
  private Integer deleted;
}
