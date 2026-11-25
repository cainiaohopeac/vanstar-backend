package org.dromara.label.common;

/**
 * 标注对象类型常量（标注数据 objects.type / geometry.kind）。
 *
 * <p>图片任务默认为多边形，点云任务为点云实例分割。</p>
 */
public final class AnnotationObjectType {

  /** 多边形（2D） */
  public static final String POLYGON = "polygon";

  /** 点云实例分割（3D） */
  public static final String POINT_CLOUD_INSTANCE = "point_cloud_instance";

  private AnnotationObjectType() {}
}
