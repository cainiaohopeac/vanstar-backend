package org.dromara.label.common;

/**
 * 数据文件类型常量（label_project.data_type / label_task.data_type / label_job.data_type / label_asset.media_type）。
 *
 * <p>项目、任务、作业的 {@code dataType} 与数据资产的 {@code mediaType} 取同一套值。</p>
 */
public final class MediaType {

  private MediaType() {
  }

  /** 图片。 */
  public static final String IMAGE = "image";
  /** 3D 点云。 */
  public static final String POINT_CLOUD = "point_cloud";
  /** 文本（命名实体、实体关系、情感分类）。 */
  public static final String TEXT = "text";
  /** 混合类型（项目同时含图片与点云，拆分作业时按图片/点云分别建作业）。 */
  public static final String MIXED = "mixed";
}
