package org.dromara.label.common;

/**
 * 上传数据状态常量（label_upload.status）。
 *
 * <p>上传数据创建即视为已入库完成；历史数据可能存在 parsed 值。</p>
 */
public final class UploadDataStatus {

  /** 已完成（新建上传数据的初始状态） */
  public static final String COMPLETED = "completed";

  private UploadDataStatus() {}
}
