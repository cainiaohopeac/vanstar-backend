package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 标注对象级批注（框级批注）VO
 */
@Data
public class ObjectCommentVO implements Serializable {

    private Long id;
    private Long jobId;
    private Long assetId;
    /** 标注对象 key（为空表示整图批注） */
    private String objectKey;
    /** 产生环节（label 标注 / review 审核） */
    private String node;
    /** 批注类型（错误内容：漏点/漏标/不贴合/混点/类别错误或自定义类型） */
    private String commentType;
    private String content;
    /** 3D 位置坐标：条目级位置批注使用；实例批注时为空 */
    private Double posX;
    private Double posY;
    private Double posZ;
    /** 状态（open 待处理 / corrected 已修正） */
    private String status;
    private Long createdBy;
    /** 创建人昵称，便于前端直接展示 */
    private String createdByName;
    private LocalDateTime createTime;
}
