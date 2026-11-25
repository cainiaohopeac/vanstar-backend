package org.dromara.label.common;

/**
 * 数据资产元数据来源标签常量（label_asset.meta_json 的 source 字段）。
 *
 * <p>记录每条资产由哪个导入链路产生，仅用于审计追溯，不参与业务流转。</p>
 */
public final class AssetMetaSource {

  /** 手工登记 */
  public static final String MANUAL = "manual";

  /** zip 压缩包导入 */
  public static final String ZIP_UPLOAD = "zip_upload";

  /** 大文件（分片）上传 */
  public static final String BIG_FILE_UPLOAD = "big_file_upload";

  /** MinIO 对象直传 */
  public static final String MINIO_UPLOAD = "minio_upload";

  private AssetMetaSource() {}
}
