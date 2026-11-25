package org.dromara.label.vo;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * 成员工时分析：分日工时矩阵 + 效率指标。
 */
@Data
public class StatsWorkHourVO implements Serializable {

    private List<String> dates;

    private List<StatsWorkHourRowVO> rows;

    private Double totalHours;

    /** 是否存在按「领取-完成时间差」兜底估算的行，前端需展示口径提示。 */
    private Boolean estimated;
}
