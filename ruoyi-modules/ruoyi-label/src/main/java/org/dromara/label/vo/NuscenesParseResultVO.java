package org.dromara.label.vo;

import java.io.Serializable;
import lombok.Data;

/**
 * nuScenes 元数据解析结果（POST /nuscenes/parse 出参）。
 */
@Data
public class NuscenesParseResultVO implements Serializable {

    /** 上传数据ID */
    private Long uploadId;
    /** 元数据归档记录ID */
    private Long metaId;
    /** nuScenes 版本目录名（v1.0-mini 等） */
    private String versionDir;
    /** 解析状态（parsed / failed） */
    private String status;
    /** 说明（失败原因 / 统计摘要） */
    private String remark;
    /** 建立的点云-相机关联条数（相机图通道数） */
    private Integer cameraMapCount;
    /** 参与预标注的数据资产总数 */
    private Integer preAnnotationAssetTotal;
    /** 匹配到 nuScenes 帧并生成预标注的资产数 */
    private Integer preAnnotationMatched;
    /** 生成的预标注框总数 */
    private Integer preAnnotationBoxTotal;
    /** nuScenes 实例总数（按 instance_token 去重） */
    private Integer instanceCount;
    /** 存在漏标帧的实例数 */
    private Integer gapInstanceCount;
    /** 漏标帧总数 */
    private Integer missingFrameTotal;
}
