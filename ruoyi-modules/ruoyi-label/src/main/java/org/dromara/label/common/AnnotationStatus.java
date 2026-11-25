package org.dromara.label.common;

/**
 * 标注保存状态常量（label_annotation.annotation_status）。
 */
public final class AnnotationStatus {

  private AnnotationStatus() {
  }

  /** 草稿（保存未提交）。 */
  public static final String DRAFT = "draft";
  /** 已提交（提交审核）。 */
  public static final String SUBMITTED = "submitted";
}
