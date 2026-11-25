package org.dromara.label.dto;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * 统计分析统一筛选条件。
 *
 * <p>字段约定：
 * <ul>
 *   <li>projectId / taskId / teamId：层级收敛，teamId 命中任务团队或项目关联团队；</li>
 *   <li>nodeKey：工作流节点标识（如 label、review_1），为空表示全部节点；</li>
 *   <li>startDate / endDate：yyyy-MM-dd，服务端缺省补「近 7 天」，跨度上限 92 天；</li>
 *   <li>memberIds：成员细筛，空表示不限；</li>
 *   <li>limit：排行/明细返回条数，默认 50，上限 200。</li>
 * </ul>
 */
@Data
public class StatsQueryDTO implements Serializable {

    private Long projectId;

    private Long taskId;

    private Long teamId;

    private String nodeKey;

    /** 起始日期 yyyy-MM-dd。 */
    private String startDate;

    /** 结束日期 yyyy-MM-dd。 */
    private String endDate;

    private List<Long> memberIds;

    /** 条目细筛（仅「成员标签工作量」卡片使用）：选择单个或多个条目统计，空表示全部条目。 */
    private List<Long> entryIds;

    /** 批次细筛（帧工作量 / 每日条目数等卡片使用）：选择单个或多个批次，空表示全部批次。 */
    private List<Long> batchIds;

    private Integer limit;
}
