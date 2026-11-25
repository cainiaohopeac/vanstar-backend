package org.dromara.label.vo;

import java.io.Serializable;
import lombok.Data;

/**
 * 统计分析页顶部概览卡片。
 */
@Data
public class StatsOverviewVO implements Serializable {

    /** 完成条目数。 */
    private Long entryDone;

    /** 标签产出数。 */
    private Long tagCount;

    /** 处理单元（帧/数据条）总数。 */
    private Long frameTotal;

    private Long labeledFrames;

    private Long emptyFrames;

    /** 参与人数。 */
    private Long activeMembers;

    /** 人均完成条目数。 */
    private Double avgEntriesPerMember;

    /** 单条目平均耗时（秒）。 */
    private Long avgSecondsPerEntry;

    /** 有效工时合计（小时）。 */
    private Double workHours;

    /** 一次通过率（%），无审核数据时为 null。 */
    private Double firstPassRate;

    /** 驳回率（%），无审核数据时为 null。 */
    private Double rejectRate;
}
