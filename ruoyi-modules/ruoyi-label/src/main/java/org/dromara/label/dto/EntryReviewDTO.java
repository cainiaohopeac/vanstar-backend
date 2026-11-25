package org.dromara.label.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 条目级审核请求（领取审核 / 审核通过 / 审核驳回共用）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EntryReviewDTO implements Serializable {

    /** 待审核的数据条目标（label_asset.id）。 */
    private List<Long> assetIds;

    /** 审核意见（驳回时作为退回原因，通过时可选）。 */
    private String comment;

    /**
     * 工作流节点标识（可选）：节点视图领取时由任务节点服务传入（review_N）。
     * 领取资格校验与节点记录归属优先使用该节点，而非任务级有效节点——
     * 任务中部分条目仍在标注时，任务有效节点仍是 label，但已提交审核的条目
     * 已流转到审批节点，审批节点成员应可领取，不能被任务级节点拦截。
     * 未传时（「我的审核」跨任务入口）保持原任务级有效节点口径。
     */
    private String nodeKey;
}
