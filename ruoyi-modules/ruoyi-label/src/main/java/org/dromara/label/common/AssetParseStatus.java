package org.dromara.label.common;

/**
 * 数据资产解析状态常量（label_asset.parse_status 存储值与条目列表聚合展示值）。
 *
 * <p>注意值域差异：单资产存储值为 parsed/failed 等（见 insertAsset / retryAsset）；
 * 条目列表聚合展示值（label_data_entry.parse_status 冗余列）只有 failed/success ——
 * 条目内任一未删资产解析失败即 failed，否则 success（含无资产条目）。</p>
 */
public final class AssetParseStatus {

  private AssetParseStatus() {
  }

  /** 解析失败：单资产存储值，同时是条目聚合展示的失败值。 */
  public static final String FAILED = "failed";

  /** 条目聚合展示：全部解析通过（label_data_entry.parse_status 冗余列值）。 */
  public static final String SUCCESS = "success";
}
