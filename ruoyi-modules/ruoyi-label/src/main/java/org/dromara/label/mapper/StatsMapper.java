package org.dromara.label.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.dromara.label.dto.StatsHeartbeatDTO;
import org.dromara.label.dto.StatsQueryDTO;
import org.dromara.label.vo.StatsDailyCellVO;
import org.dromara.label.vo.StatsMemberEntryVO;
import org.dromara.label.vo.StatsMemberOutputVO;
import org.dromara.label.vo.StatsNodeOptionVO;
import org.dromara.label.vo.StatsOptionVO;
import org.dromara.label.vo.StatsQualityVO;
import org.dromara.label.vo.StatsTagClassVO;
import org.dromara.label.vo.StatsTagKindVO;
import org.dromara.label.vo.StatsWorkCellVO;

/**
 * 统计分析域数据访问。
 *
 * <p>统一口径：以「节点完成记录」（{@code label_entry_node.status = 'done'}）为统计基础，
 * 按 {@code done_time} 归属工作日；帧/标签通过「条目 -> 资产 -> 标注」链路聚合。
 */
public interface StatsMapper {

    /** 成员条目工作量（完成口径）。 */
    List<StatsMemberEntryVO> selectMemberEntries(@Param("q") StatsQueryDTO q);

    /** 成员辅助动作（处理中/跳过/放弃），按 update_time 归属。 */
    List<StatsMemberEntryVO> selectMemberDisposals(@Param("q") StatsQueryDTO q);

    /** 成员标签与帧产出；labelOnly=true 时只统计标注类节点（审核节点下不重复计入产出）。 */
    List<StatsMemberOutputVO> selectMemberOutput(@Param("q") StatsQueryDTO q,
        @Param("labelOnly") boolean labelOnly);

    /** 成员标签按几何类别（点/线/2D框/2D分割/3D框/3D分割）聚合；q.entryIds 支持条目细筛；labelOnly 恒为 false。 */
    List<StatsTagKindVO> selectMemberTagKinds(@Param("q") StatsQueryDTO q,
        @Param("labelOnly") boolean labelOnly);

    /** 成员标签按「几何类别 × 标签类」聚合（层级表头表格）；q.entryIds 支持条目细筛；labelOnly 恒为 false。 */
    List<StatsTagClassVO> selectMemberTagClasses(@Param("q") StatsQueryDTO q,
        @Param("labelOnly") boolean labelOnly);

    /** 每日完成条目数。 */
    List<StatsDailyCellVO> selectDailyEntries(@Param("q") StatsQueryDTO q);

    /** 每日帧数（metric=frame）或标签数（metric=tag）；labelOnly 语义同 selectMemberOutput。 */
    List<StatsDailyCellVO> selectDailyOutput(@Param("q") StatsQueryDTO q, @Param("metric") String metric,
        @Param("labelOnly") boolean labelOnly);

    /** 每日有效工时（心跳采集）。 */
    List<StatsWorkCellVO> selectWorkSessionDaily(@Param("q") StatsQueryDTO q);

    /** 每日工时估算（领取-完成时间差，单条按 capSeconds 封顶）。 */
    List<StatsWorkCellVO> selectEntrySpanDaily(@Param("q") StatsQueryDTO q,
        @Param("capSeconds") int capSeconds);

    /** 成员质量指标。 */
    List<StatsQualityVO> selectMemberQuality(@Param("q") StatsQueryDTO q);

    /** 节点下拉项。 */
    List<StatsNodeOptionVO> selectNodeOptions(@Param("q") StatsQueryDTO q);

    /** 团队下拉项。 */
    List<StatsOptionVO> selectTeamOptions();

    /** 项目下拉项。 */
    List<StatsOptionVO> selectProjectOptions(@Param("q") StatsQueryDTO q);

    /** 任务下拉项。 */
    List<StatsOptionVO> selectTaskOptions(@Param("q") StatsQueryDTO q);

    /** 成员下拉项（范围内有处理记录的成员）。 */
    List<StatsOptionVO> selectMemberOptions(@Param("q") StatsQueryDTO q);

    /** 条目下拉项（按项目/任务过滤，供标签工作量条目细筛）。 */
    List<StatsOptionVO> selectEntryOptions(@Param("q") StatsQueryDTO q);

    /** 批次下拉项（按项目过滤，供帧工作量/每日条目数批次细筛）。 */
    List<StatsOptionVO> selectBatchOptions(@Param("q") StatsQueryDTO q);

    /** 工时心跳累加（按 人+任务+节点+日期 唯一）。 */
    int upsertWorkSession(@Param("userId") Long userId, @Param("dto") StatsHeartbeatDTO dto,
        @Param("workDate") String workDate, @Param("seconds") int seconds);

    /** 查询当日累计有效秒数（心跳上报后回显）。 */
    Long selectWorkSessionSeconds(@Param("userId") Long userId, @Param("taskId") Long taskId,
        @Param("nodeKey") String nodeKey, @Param("workDate") String workDate);
}
