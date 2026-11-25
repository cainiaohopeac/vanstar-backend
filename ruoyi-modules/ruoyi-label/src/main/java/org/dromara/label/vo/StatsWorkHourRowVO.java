package org.dromara.label.vo;

import java.io.Serializable;
import java.util.Map;
import lombok.Data;

/**
 * 工时分日矩阵单行。
 */
@Data
public class StatsWorkHourRowVO implements Serializable {

    private Long memberId;

    private String memberName;

    /** 日期 -> 小时数（保留两位小数）。 */
    private Map<String, Double> hours;

    private Double totalHours;

    /** 当日完成条目数合计。 */
    private Long doneCount;

    /** 单条目平均耗时（秒）。 */
    private Long avgSecondsPerEntry;

    /** 每小时产出条目数。 */
    private Double entriesPerHour;

    /** 该行是否含估算工时（无心跳数据时按领取-完成时间差兜底）。 */
    private Boolean estimated;
}
