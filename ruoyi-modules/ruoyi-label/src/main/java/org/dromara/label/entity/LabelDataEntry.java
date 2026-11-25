package org.dromara.label.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 数据条目（label_data_entry）。
 *
 * <p>文本标注下：<strong>一个上传文件 = 一个条目 = 一个资产</strong>。
 * 条目是任务分配与审核的单位，资产存正文与句边界。
 */
@Data
public class LabelDataEntry implements Serializable {

  /** 条目ID */
  private Long id;
  /** 项目ID */
  private Long projectId;
  /** 批次ID */
  private Long batchId;
  /** 上传数据ID（本链路暂不使用） */
  private Long uploadId;
  /** 条目编号（系统生成，唯一） */
  private String entryNo;
  /** 条目名称（默认取上传文件名） */
  private String entryName;
  /** 上传源文件（原始文件名） */
  private String sourceFile;
  /** 上传内序号（从 1 开始） */
  private Integer seqNo;
  /** 条目内数据条数 */
  private Integer assetCount;
  /** 分割方式：whole 不分割（文本固定 whole） */
  private String splitMode;
  /** 条目聚合媒体类型 */
  private String mediaType;
  /** 条目聚合解析状态 */
  private String parseStatus;
  /** 条目标注状态：pending / labeling / labeled ... */
  private String labelStatus;
  /** 条目首个资产 id（列表展示用） */
  private Long firstAssetId;
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
