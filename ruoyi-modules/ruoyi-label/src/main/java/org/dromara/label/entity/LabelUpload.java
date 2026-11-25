package org.dromara.label.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 上传记录（label_upload）。
 *
 * <p>文本导入需要先建一条上传记录：{@code label_data_entry.upload_id} 非空且无默认值，
 * 不建记录直接插条目会被数据库拒绝；同时「导入记录」页也依赖该表展示导入历史。
 */
@Data
public class LabelUpload implements Serializable {

  /** 上传ID */
  private Long id;
  /** 项目ID */
  private Long projectId;
  /** 批次ID */
  private Long batchId;
  /** 上传编号 */
  private String uploadNo;
  /** 文件名 */
  private String fileName;
  /** 文件大小 */
  private Long fileSize;
  /** MinIO 对象名 */
  private String objectName;
  /** 来源类型（文本导入固定 text） */
  private String sourceType;
  /** 资产数量 */
  private Integer assetCount;
  /** 状态：parsing / success / failed */
  private String status;
  /** 备注 */
  private String remark;
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
