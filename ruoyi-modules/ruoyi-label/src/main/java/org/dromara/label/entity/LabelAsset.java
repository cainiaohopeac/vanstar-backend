package org.dromara.label.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 数据资产（label_asset）在文本链路用到的字段。
 *
 * <p>一篇文本 = 一个 entry + 一个 asset：
 * {@code fileUrl} 指向规范化后的纯文本（MinIO），
 * {@code metaJson} 存句边界与字数等导入时解析出的元信息。
 */
@Data
public class LabelAsset implements Serializable {

  /** 资产ID */
  private Long id;
  /** 项目ID */
  private Long projectId;
  /** 批次ID */
  private Long batchId;
  /** 上传数据ID */
  private Long uploadId;
  /** 所属条目ID */
  private Long entryId;
  /** 媒体类型（text） */
  private String mediaType;
  /** 文件名 */
  private String fileName;
  /** 文件地址（MinIO 相对路径，以 /uploads/ 开头） */
  private String fileUrl;
  /** 文件大小（字节） */
  private Long fileSize;
  /** 解析状态 */
  private String parseStatus;
  /** 元信息 JSON（句边界、字数、句数等） */
  private String metaJson;
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
