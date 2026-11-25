package org.dromara.label.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 标注对象级批注（框级批注 / 条目位置批注）新增入参。
 *
 * <p>审核节点批注由「批注类型（错误内容，下拉选择）」+「描述（≤200 字）」组成，
 * 两者至少填一项；标注环节的历史调用只传 content，仍然兼容。</p>
 */
@Data
public class ObjectCommentDTO implements Serializable {

    /** 资产（图片/点云帧）ID */
    private Long assetId;
    /** 标注对象 key（为空表示条目级批注） */
    private String objectKey;
    /** 产生环节（label 标注 / review 审核），为空按标注环节处理 */
    private String node;

    /**
     * 批注类型（错误内容）：漏点 / 漏标 / 不贴合 / 混点 / 类别错误，或审核节点上自定义的类型。
     * 取自当前审核节点的配置，长度与 label_object_comment.comment_type 列宽一致。
     */
    @Size(max = 32, message = "批注类型不能超过 32 字")
    private String commentType;

    /** 批注描述（审核员填写，限 200 字；与批注类型至少填一项） */
    @Size(max = 200, message = "批注描述不能超过 200 字")
    private String content;

    /** 3D 位置坐标：条目级位置批注使用；实例批注时为空 */
    private Double posX;
    private Double posY;
    private Double posZ;
}
