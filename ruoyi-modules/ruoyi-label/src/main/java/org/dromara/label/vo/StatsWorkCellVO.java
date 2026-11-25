package org.dromara.label.vo;

import java.io.Serializable;
import lombok.Data;

/**
 * 工时明细单元（Mapper 内部使用）：心跳累计或领取-完成时间差估算。
 */
@Data
public class StatsWorkCellVO implements Serializable {

    private Long memberId;

    private String memberName;

    /** yyyy-MM-dd。 */
    private String day;

    /** 有效秒数。 */
    private Long seconds;

    /** 当日完成条目数（估算路径下用于计算效率）。 */
    private Long doneCount;
}
