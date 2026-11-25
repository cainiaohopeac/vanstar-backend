package org.dromara.label.vo;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * 每日矩阵（成员 × 日期），用于「每日标注条目数 / 帧数 / 标签数」表格。
 */
@Data
public class StatsDailyMatrixVO implements Serializable {

    /** 指标标识：entry / frame / tag。 */
    private String metric;

    /** 单位文案：条 / 帧 / 个。 */
    private String unit;

    /** 连续日期序列 yyyy-MM-dd。 */
    private List<String> dates;

    private List<StatsDailyRowVO> rows;

    /** 每日列合计，长度与 dates 一致。 */
    private List<Long> columnTotals;

    /** 总计。 */
    private Long total;
}
