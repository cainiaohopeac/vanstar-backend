package org.dromara.label.vo;

import java.io.Serializable;
import lombok.Data;

/**
 * 统计筛选下拉项（团队 / 项目 / 任务 / 成员）。
 */
@Data
public class StatsOptionVO implements Serializable {

    private Long id;

    private String name;
}
