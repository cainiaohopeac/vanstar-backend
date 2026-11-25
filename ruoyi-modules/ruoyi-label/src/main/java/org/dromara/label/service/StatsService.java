package org.dromara.label.service;

import java.util.List;
import org.dromara.label.dto.StatsHeartbeatDTO;
import org.dromara.label.dto.StatsQueryDTO;
import org.dromara.label.vo.StatsDailyMatrixVO;
import org.dromara.label.vo.StatsFilterVO;
import org.dromara.label.vo.StatsMemberEntryVO;
import org.dromara.label.vo.StatsMemberOutputVO;
import org.dromara.label.vo.StatsOverviewVO;
import org.dromara.label.vo.StatsQualityVO;
import org.dromara.label.vo.StatsTagClassVO;
import org.dromara.label.vo.StatsWorkHourVO;

/**
 * 统计分析域服务：产能（条目/标签/帧）、效率（工时）、质量（一次通过率）。
 *
 * <p>所有查询共享 {@link StatsQueryDTO} 筛选（节点 + 团队 + 项目/任务 + 时间 + 成员）。</p>
 */
public interface StatsService {

    /** 筛选项：节点、团队、项目、任务、成员。 */
    StatsFilterVO filters(StatsQueryDTO dto);

    /** 成员条目工作量。 */
    List<StatsMemberEntryVO> memberEntries(StatsQueryDTO dto);

    /** 成员标签工作量。 */
    List<StatsMemberOutputVO> memberTags(StatsQueryDTO dto);

    /** 成员标签按「几何类别 × 标签类」聚合（层级表头表格）。 */
    List<StatsTagClassVO> memberTagClasses(StatsQueryDTO dto);

    /** 成员帧工作量（有标签/无标签/合计）。 */
    List<StatsMemberOutputVO> memberFrames(StatsQueryDTO dto);

    /** 每日标注条目数矩阵。 */
    StatsDailyMatrixVO dailyEntries(StatsQueryDTO dto);

    /** 每日标注帧数矩阵。 */
    StatsDailyMatrixVO dailyFrames(StatsQueryDTO dto);

    /** 每日标注标签数矩阵。 */
    StatsDailyMatrixVO dailyTags(StatsQueryDTO dto);

    /** 工时分析（分日矩阵 + 效率）。 */
    StatsWorkHourVO workHours(StatsQueryDTO dto);

    /** 概览卡片。 */
    StatsOverviewVO overview(StatsQueryDTO dto);

    /** 成员质量指标。 */
    List<StatsQualityVO> quality(StatsQueryDTO dto);

    /** 工时心跳上报，返回当日累计秒数。 */
    long heartbeat(StatsHeartbeatDTO dto);
}
