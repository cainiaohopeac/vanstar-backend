package org.dromara.label.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 任务条目级分配请求。
 *
 * <p>mode = claim 表示「领取标注」：把选中条目确定为自己（当前登录用户）为标注员；
 * mode = assign 表示「分配」：把选中条目分配给指定的其他标注员，仅团队负责人可用。</p>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EntryAssignDTO implements Serializable {

    /** 待分配的数据条目标（label_asset.id） */
    private List<Long> assetIds;
    /** 分配模式：claim(领取)/assign(分配) */
    private String mode;
    /** 目标标注员ID（claim 时忽略，取当前登录用户） */
    private Long assigneeId;
    /** 目标标注员名称（按名称解析，兼容前端下拉） */
    private String assigneeName;
    /** 目标审核(质检)员ID（可选） */
    private Long reviewerId;
    /** 目标审核(质检)员名称（可选） */
    private String reviewerName;
    /** 工作流节点标识（label / review_1 / review_2 ...），节点切换场景下必填 */
    private String nodeKey;
    /** 批次约束（claim 领取时可选）：只允许领取该批次的条目，批次与批次之间不混领 */
    private Long batchId;
}
