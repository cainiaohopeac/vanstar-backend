package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 预标注作用范围内的数据资产（轻量视图）。
 *
 * <p>预标注按「文件名」匹配 nuScenes 帧，因此这里只取匹配所需的最小字段集。</p>
 */
@Data
public class PreAnnotationAssetVO implements Serializable {

    private Long assetId;
    private Long projectId;
    private Long batchId;
    private Long uploadId;
    private Long entryId;
    /** 文件名（如 n008-...__LIDAR_TOP__1533151603547590.bin） */
    private String fileName;
    /** 媒体类型：image / point_cloud */
    private String mediaType;
    private String fileUrl;
    /**
     * 图片像素宽（入库时解析的<b>原图</b>尺寸）。
     *
     * <p>YOLO 标签是归一化坐标，必须乘以原图宽高才能还原成像素坐标；
     * 点云资产本字段为空。历史数据可能未解析（0 / null），此时 YOLO 无法反算。
     */
    private Integer width;
    /** 图片像素高（同上，YOLO 归一化坐标反算用） */
    private Integer height;
}
