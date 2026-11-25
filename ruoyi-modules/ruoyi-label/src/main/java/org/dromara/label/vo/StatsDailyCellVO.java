package org.dromara.label.vo;

import java.io.Serializable;
import lombok.Data;

/**
 * 每日矩阵查询结果单元（Mapper 内部使用，Service 层聚合成矩阵）。
 */
@Data
public class StatsDailyCellVO implements Serializable {

    private Long memberId;

    private String memberName;

    /** yyyy-MM-dd。 */
    private String day;

    private Long value;
}
