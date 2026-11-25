package org.dromara.label.entity;

import java.io.Serializable;
import lombok.Data;

/**
 * nuScenes 元数据归档（label_nuscenes_meta）实体：
 * 上传数据中的 v1.0-* 元数据 JSON 原样归档到 MinIO 后的登记记录，
 * 支撑相机参考图关联、自动预标注与原格式导出还原。
 */
@Data
public class LabelNuscenesMeta implements Serializable {

  /** 元数据ID */
  private Long id;
  /** 项目ID */
  private Long projectId;
  /** 数据批次ID */
  private Long batchId;
  /** 上传数据ID */
  private Long uploadId;
  /** nuScenes 版本目录名（v1.0-mini / v1.0-trainval 等） */
  private String versionDir;
  /** 元数据压缩包的 MinIO 对象Key（v1.0-* 下全部 JSON 原样打包） */
  private String metaObjectKey;
  /** 状态：parsed 已解析 / failed 解析失败 */
  private String status;
  /** 解析结果说明（失败原因 / 预标注统计） */
  private String remark;
  /** 操作人ID（写 created_by / updated_by） */
  private Long userId;
}
