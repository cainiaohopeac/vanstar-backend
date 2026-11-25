package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 上传/导入入库结果 VO
 */
@Data
public class UploadResultVO implements Serializable {

    /** 新增资产数 */
    private int savedCount;
    /** 图片资产数 */
    private int imageCount;
    /** 点云资产数 */
    private int pointCloudCount;
    /** 跳过/忽略数 */
    private int skippedCount;
    /** 回调场景下的资产ID（zip 批量上传为空） */
    private Long assetId;
    /** 本次上传对应的上传数据ID（批次下独立的上传单元） */
    private Long uploadId;
}
