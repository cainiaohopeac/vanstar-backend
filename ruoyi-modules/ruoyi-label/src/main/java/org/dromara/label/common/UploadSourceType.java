package org.dromara.label.common;

/**
 * 上传数据来源类型常量（label_upload.source_type）。
 *
 * <p>标识一条上传数据记录由哪种导入方式产生。</p>
 */
public final class UploadSourceType {

  /** 手工登记（JSON 清单/单文件登记） */
  public static final String MANUAL = "manual";

  /** zip 压缩包导入 */
  public static final String ZIP = "zip";

  /** 单文件直传 */
  public static final String SINGLE = "single";

  /** 文件夹批量导入 */
  public static final String FOLDER = "folder";

  private UploadSourceType() {}
}
