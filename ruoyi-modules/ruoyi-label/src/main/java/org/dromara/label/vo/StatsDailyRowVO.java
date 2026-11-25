package org.dromara.label.vo;

import java.io.Serializable;
import lombok.Data;

/**
 * 每日矩阵单行：一名成员在某指标下的分日数据。
 */
@Data
public class StatsDailyRowVO implements Serializable {

    private Long memberId;

    private String memberName;

    /** 日期 -> 数值，缺失日期补 0。 */
    private java.util.Map<String, Long> values;

    /** 行合计。 */
    private Long total;
}
