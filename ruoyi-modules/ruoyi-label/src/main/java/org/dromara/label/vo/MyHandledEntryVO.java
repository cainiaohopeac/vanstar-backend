package org.dromara.label.vo;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 「我的领取条目」VO：跨任务、跨节点聚合当前用户作为 operator 领取过的数据条目，
 * 用于首页「总览」展示，节点流转后仍可看到本人历史操作记录。
 *
 * <p>释放条目（operator_id 被 resetPendingClaimed 清空）天然不会出现在此视图；</p>
 * <p>节点状态为 claimed/skipped/abandoned/done 时显示，已流转到下一节点不再可见原节点视角的同样会被覆盖。</p>
 */
@Data
public class MyHandledEntryVO implements Serializable {

    private Long entryNodeId;
    private Long entryId;
    private String entryNo;
    private String entryName;
    private Long batchId;
    private String batchName;
    private Long taskId;
    private String taskNo;
    private String taskName;
    private Long projectId;
    private String projectName;
    /** 工作流节点 key，如 label / review_1。 */
    private String nodeKey;
    /** 节点类型：label / review。 */
    private String nodeType;
    /** 节点状态：claimed / skipped / abandoned / done。 */
    private String nodeStatus;
    private Long operatorId;
    private Date claimTime;
    private Date skipTime;
    private Date abandonTime;
    private Date doneTime;
    /** 业务侧条目状态（标注/审核），便于前端在不打开详情时也能定位条目所处阶段。 */
    private String labelStatus;
    private String reviewStatus;
    /** 关联作业 ID（前端跳转标注工作台/审核工作台用）。 */
    private Long jobId;
}