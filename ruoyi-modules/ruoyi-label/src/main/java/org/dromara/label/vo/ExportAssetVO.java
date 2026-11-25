package org.dromara.label.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;

/**
 * 导出数据集中资产条目 VO
 */
@Data
public class ExportAssetVO implements Serializable {

    private Long assetId;
    private String mediaType;
    private String fileName;
    private String fileUrl;
    private String thumbnailUrl;
    private Long fileSize;
    /** 图片像素宽（入库时解析，导出点位 JSON 的 size 字段用；缺省为 null；不进入原生日平台 JSON，避免影响既有格式） */
    @JsonIgnore
    private Integer width;
    /** 图片像素高（入库时解析，导出点位 JSON 的 size 字段用；缺省为 null；不进入原生日平台 JSON，避免影响既有格式） */
    @JsonIgnore
    private Integer height;
    /** 所属上传数据ID（nuScenes 原格式导出定位元数据归档用） */
    private Long uploadId;
    /** 帧状态（normal/invalid；null 按 normal 处理） */
    @JsonIgnore
    private String frameFlag;
    /** 无效帧原因（仅 frameFlag=invalid 时有值） */
    @JsonIgnore
    private String invalidReason;
    /** 元数据（解析后的对象） */
    private Object meta;
    /** SQL 暂存列，不对外输出 */
    @JsonIgnore
    private String metaJson;
}
