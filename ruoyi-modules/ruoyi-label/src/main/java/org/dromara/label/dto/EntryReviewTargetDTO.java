package org.dromara.label.dto;

import lombok.Data;

/**
 * 条目审核目标快照（Mapper 返回专用 DTO）。
 *
 * <p>由 {@code ReviewMapper.selectEntryReviewTargets} 返回，承载条目级审核的
 * 领取前校验（已标注/已修订+未审核）、审核中校验（审核中/复审中+本人领取）、
 * 通知与审核记录写入所需的全部字段。</p>
 *
 * <p>非数据库对应实体（不实现 Serializable）。</p>
 */
@Data
public class EntryReviewTargetDTO {

    /** 数据条目 ID */
    private Long entryId;
    /** 条目名称 */
    private String entryName;
    /** 所属任务 ID */
    private Long taskId;
    /** 标注状态（labeled / revised / unrevised 等） */
    private String labelStatus;
    /** 审核状态（none / reviewing / re_reviewing / passed / rejected 等） */
    private String reviewStatus;
    /** 当前标注员 ID，未分配时为 null */
    private Long assigneeId;
    /** 当前审核员 ID，未领取时为 null */
    private Long reviewerId;
    /** 承载该条目数据的作业 ID，未归入作业时为 null */
    private Long jobId;
}