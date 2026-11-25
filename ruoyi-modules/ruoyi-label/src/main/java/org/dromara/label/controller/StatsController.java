package org.dromara.label.controller;

import java.util.List;
import org.dromara.label.common.Result;
import org.dromara.label.dto.StatsHeartbeatDTO;
import org.dromara.label.dto.StatsQueryDTO;
import org.dromara.label.service.StatsService;
import org.dromara.label.vo.StatsDailyMatrixVO;
import org.dromara.label.vo.StatsFilterVO;
import org.dromara.label.vo.StatsMemberEntryVO;
import org.dromara.label.vo.StatsMemberOutputVO;
import org.dromara.label.vo.StatsTagClassVO;
import org.dromara.label.vo.StatsOverviewVO;
import org.dromara.label.vo.StatsQualityVO;
import org.dromara.label.vo.StatsWorkHourVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统计分析域接口。
 *
 * <p>页面顶部的「节点选择 + 团队选择 + 时间范围」由前端透传为 {@link StatsQueryDTO}，
 * 各图表接口独立调用，互不阻塞。</p>
 */
@RestController
public class StatsController {

    @Autowired
    private StatsService statsService;

    /** 筛选项：节点、团队、项目、任务、成员。 */
    @GetMapping("/stats/filters")
    public Result<StatsFilterVO> filters(StatsQueryDTO query) {
        return Result.success(statsService.filters(query));
    }

    /** 成员条目工作量（柱状图）。 */
    @PostMapping("/stats/member-entries")
    public Result<List<StatsMemberEntryVO>> memberEntries(@RequestBody(required = false) StatsQueryDTO query) {
        return Result.success(statsService.memberEntries(query));
    }

    /** 成员标签工作量（柱状图）。 */
    @PostMapping("/stats/member-tags")
    public Result<List<StatsMemberOutputVO>> memberTags(@RequestBody(required = false) StatsQueryDTO query) {
        return Result.success(statsService.memberTags(query));
    }

    /** 成员标签按「几何类别 × 标签类」聚合（层级表头表格）。 */
    @PostMapping("/stats/member-tag-classes")
    public Result<List<StatsTagClassVO>> memberTagClasses(@RequestBody(required = false) StatsQueryDTO query) {
        return Result.success(statsService.memberTagClasses(query));
    }

    /** 成员帧工作量（表格：有标签帧 / 无标签帧 / 合计）。 */
    @PostMapping("/stats/member-frames")
    public Result<List<StatsMemberOutputVO>> memberFrames(@RequestBody(required = false) StatsQueryDTO query) {
        return Result.success(statsService.memberFrames(query));
    }

    /** 每日标注条目数。 */
    @PostMapping("/stats/daily/entries")
    public Result<StatsDailyMatrixVO> dailyEntries(@RequestBody(required = false) StatsQueryDTO query) {
        return Result.success(statsService.dailyEntries(query));
    }

    /** 每日标注帧数。 */
    @PostMapping("/stats/daily/frames")
    public Result<StatsDailyMatrixVO> dailyFrames(@RequestBody(required = false) StatsQueryDTO query) {
        return Result.success(statsService.dailyFrames(query));
    }

    /** 每日标注标签数。 */
    @PostMapping("/stats/daily/tags")
    public Result<StatsDailyMatrixVO> dailyTags(@RequestBody(required = false) StatsQueryDTO query) {
        return Result.success(statsService.dailyTags(query));
    }

    /** 工时分析（成员 × 日期）。 */
    @PostMapping("/stats/work-hours")
    public Result<StatsWorkHourVO> workHours(@RequestBody(required = false) StatsQueryDTO query) {
        return Result.success(statsService.workHours(query));
    }

    /** 概览卡片。 */
    @PostMapping("/stats/overview")
    public Result<StatsOverviewVO> overview(@RequestBody(required = false) StatsQueryDTO query) {
        return Result.success(statsService.overview(query));
    }

    /** 成员质量：一次通过率 / 驳回率 / 返修次数。 */
    @PostMapping("/stats/quality")
    public Result<List<StatsQualityVO>> quality(@RequestBody(required = false) StatsQueryDTO query) {
        return Result.success(statsService.quality(query));
    }

    /**
     * 工时心跳上报：前端每 60s 调用一次，返回当日累计有效秒数。
     * 单次秒数服务端收敛到 1~600，防止挂机刷工时。
     */
    @PostMapping("/stats/work-session/heartbeat")
    public Result<Long> heartbeat(@RequestBody StatsHeartbeatDTO dto) {
        return Result.success(statsService.heartbeat(dto));
    }
}
