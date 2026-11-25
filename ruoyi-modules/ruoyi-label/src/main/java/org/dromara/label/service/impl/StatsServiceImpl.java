package org.dromara.label.service.impl;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.label.dto.StatsHeartbeatDTO;
import org.dromara.label.dto.StatsQueryDTO;
import org.dromara.label.mapper.StatsMapper;
import org.dromara.label.service.BaseService;
import org.dromara.label.service.StatsService;
import org.dromara.label.vo.StatsDailyCellVO;
import org.dromara.label.vo.StatsDailyMatrixVO;
import org.dromara.label.vo.StatsDailyRowVO;
import org.dromara.label.vo.StatsFilterVO;
import org.dromara.label.vo.StatsMemberEntryVO;
import org.dromara.label.vo.StatsMemberOutputVO;
import org.dromara.label.vo.StatsNodeOptionVO;
import org.dromara.label.vo.StatsOverviewVO;
import org.dromara.label.vo.StatsQualityVO;
import org.dromara.label.vo.StatsTagClassVO;
import org.dromara.label.vo.StatsTagKindVO;
import org.dromara.label.vo.StatsWorkCellVO;
import org.dromara.label.vo.StatsWorkHourRowVO;
import org.dromara.label.vo.StatsWorkHourVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 统计分析域服务实现。
 *
 * <p>产能统一走「节点完成」口径（label_entry_node.status='done'，按 done_time 归日）；
 * 每日矩阵在 Java 侧补齐缺失日期与成员；工时优先心跳累计，缺失时回退领取-完成时间差。</p>
 */
@Service
public class StatsServiceImpl extends BaseService implements StatsService {

    private static final int DEFAULT_RANGE_DAYS = 7;
    private static final int MAX_RANGE_DAYS = 92;
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    private static final int SPAN_CAP_SECONDS = 1800;
    private static final int DEFAULT_HEARTBEAT_SECONDS = 60;
    private static final int MAX_HEARTBEAT_SECONDS = 600;

    @Autowired
    private StatsMapper statsMapper;

    @Override
    public StatsFilterVO filters(StatsQueryDTO dto) {
        StatsQueryDTO q = prepare(dto);
        StatsFilterVO vo = new StatsFilterVO();
        vo.setNodes(statsMapper.selectNodeOptions(q));
        vo.setTeams(statsMapper.selectTeamOptions());
        vo.setProjects(statsMapper.selectProjectOptions(q));
        vo.setTasks(statsMapper.selectTaskOptions(q));
        vo.setMembers(statsMapper.selectMemberOptions(memberOptionQuery(q)));
        vo.setEntries(q.getProjectId() == null ? new ArrayList<>() : statsMapper.selectEntryOptions(q));
        vo.setBatches(q.getProjectId() == null ? new ArrayList<>() : statsMapper.selectBatchOptions(q));
        vo.setDefaultNodeKey(resolveDefaultNodeKey(vo.getNodes()));
        return vo;
    }

    @Override
    public List<StatsMemberEntryVO> memberEntries(StatsQueryDTO dto) {
        StatsQueryDTO q = prepare(dto);
        List<StatsMemberEntryVO> entries = statsMapper.selectMemberEntries(q);
        Map<Long, StatsMemberEntryVO> disposalMap = new HashMap<>();
        for (StatsMemberEntryVO d : statsMapper.selectMemberDisposals(q)) {
            disposalMap.put(d.getMemberId(), d);
        }
        for (StatsMemberEntryVO e : entries) {
            StatsMemberEntryVO d = disposalMap.get(e.getMemberId());
            e.setDoneCount(nz(e.getDoneCount()));
            e.setAvgSeconds(nz(e.getAvgSeconds()));
            e.setProcessingCount(d == null ? 0L : nz(d.getProcessingCount()));
            e.setSkippedCount(d == null ? 0L : nz(d.getSkippedCount()));
            e.setAbandonedCount(d == null ? 0L : nz(d.getAbandonedCount()));
        }
        return entries;
    }

    @Override
    public List<StatsMemberOutputVO> memberTags(StatsQueryDTO dto) {
        List<StatsMemberOutputVO> rows = outputs(dto, false);
        StatsQueryDTO q = prepare(dto);
        Map<Long, Map<String, Long>> kindMap = new HashMap<>();
        for (StatsTagKindVO kind : statsMapper.selectMemberTagKinds(q, false)) {
            kindMap.computeIfAbsent(kind.getMemberId(), id -> new LinkedHashMap<>())
                .merge(kind.getKind(), nz(kind.getCnt()), Long::sum);
        }
        for (StatsMemberOutputVO row : rows) {
            row.setKinds(kindMap.getOrDefault(row.getMemberId(), Map.of()));
        }
        return rows;
    }

    @Override
    public List<StatsTagClassVO> memberTagClasses(StatsQueryDTO dto) {
        return statsMapper.selectMemberTagClasses(prepare(dto), false);
    }

    @Override
    public List<StatsMemberOutputVO> memberFrames(StatsQueryDTO dto) {
        return outputs(dto, true);
    }

    @Override
    public StatsDailyMatrixVO dailyEntries(StatsQueryDTO dto) {
        StatsQueryDTO q = prepare(dto);
        return buildMatrix(q, statsMapper.selectDailyEntries(q), "entry", "条");
    }

    @Override
    public StatsDailyMatrixVO dailyFrames(StatsQueryDTO dto) {
        StatsQueryDTO q = prepare(dto);
        return buildMatrix(q, statsMapper.selectDailyOutput(q, "frame", labelOnly(q)), "frame", "帧");
    }

    @Override
    public StatsDailyMatrixVO dailyTags(StatsQueryDTO dto) {
        StatsQueryDTO q = prepare(dto);
        return buildMatrix(q, statsMapper.selectDailyOutput(q, "tag", labelOnly(q)), "tag", "个");
    }

    @Override
    public StatsWorkHourVO workHours(StatsQueryDTO dto) {
        StatsQueryDTO q = prepare(dto);
        List<StatsWorkCellVO> sessionCells = statsMapper.selectWorkSessionDaily(q);
        List<StatsWorkCellVO> spanCells = statsMapper.selectEntrySpanDaily(q, SPAN_CAP_SECONDS);

        Map<Long, String> names = new HashMap<>();
        Map<Long, Map<String, Long>> sessionDays = groupByMember(sessionCells, names);
        Map<Long, Map<String, Long>> spanDays = groupByMember(spanCells, names);
        Map<Long, Long> doneCounts = new HashMap<>();
        for (StatsWorkCellVO cell : spanCells) {
            doneCounts.merge(cell.getMemberId(), nz(cell.getDoneCount()), Long::sum);
        }

        Set<Long> members = new LinkedHashSet<>(sessionDays.keySet());
        members.addAll(spanDays.keySet());
        List<String> dates = dateRange(q);
        List<StatsWorkHourRowVO> rows = new ArrayList<>();
        double totalHours = 0D;
        boolean anyEstimated = false;

        for (Long memberId : members) {
            Map<String, Long> days = sessionDays.getOrDefault(memberId, Map.of());
            boolean estimated = false;
            if (sum(days) <= 0) {
                days = spanDays.getOrDefault(memberId, Map.of());
                estimated = !days.isEmpty();
            }
            Map<String, Double> hours = new LinkedHashMap<>();
            long totalSeconds = 0L;
            for (String date : dates) {
                long seconds = days.getOrDefault(date, 0L);
                totalSeconds += seconds;
                hours.put(date, round2(seconds / 3600D));
            }
            double memberHours = round2(totalSeconds / 3600D);
            totalHours += memberHours;
            long doneCount = nz(doneCounts.get(memberId));

            StatsWorkHourRowVO row = new StatsWorkHourRowVO();
            row.setMemberId(memberId);
            row.setMemberName(names.get(memberId));
            row.setHours(hours);
            row.setTotalHours(memberHours);
            row.setDoneCount(doneCount);
            row.setAvgSecondsPerEntry(doneCount > 0 ? totalSeconds / doneCount : null);
            row.setEntriesPerHour(memberHours > 0 ? round2(doneCount / memberHours) : null);
            row.setEstimated(estimated);
            anyEstimated = anyEstimated || estimated;
            rows.add(row);
        }
        rows.sort(Comparator.comparing(StatsWorkHourRowVO::getTotalHours).reversed());

        StatsWorkHourVO vo = new StatsWorkHourVO();
        vo.setDates(dates);
        vo.setRows(rows);
        vo.setTotalHours(round2(totalHours));
        vo.setEstimated(anyEstimated);
        return vo;
    }

    @Override
    public StatsOverviewVO overview(StatsQueryDTO dto) {
        StatsQueryDTO q = prepare(dto);
        q.setLimit(null);

        List<StatsMemberEntryVO> entries = statsMapper.selectMemberEntries(q);
        List<StatsMemberOutputVO> outputs = statsMapper.selectMemberOutput(q, labelOnly(q));
        List<StatsQualityVO> qualityRows = statsMapper.selectMemberQuality(q);

        long entryDone = 0L;
        long weightedSeconds = 0L;
        Set<Long> memberIds = new LinkedHashSet<>();
        for (StatsMemberEntryVO e : entries) {
            long done = nz(e.getDoneCount());
            entryDone += done;
            weightedSeconds += done * nz(e.getAvgSeconds());
            memberIds.add(e.getMemberId());
        }
        long tagCount = 0L;
        long frameTotal = 0L;
        long labeledFrames = 0L;
        long emptyFrames = 0L;
        for (StatsMemberOutputVO o : outputs) {
            tagCount += nz(o.getTagCount());
            frameTotal += nz(o.getTotalFrames());
            labeledFrames += nz(o.getLabeledFrames());
            emptyFrames += nz(o.getEmptyFrames());
        }
        long reviewed = 0L;
        long firstPass = 0L;
        long rejected = 0L;
        for (StatsQualityVO row : qualityRows) {
            reviewed += nz(row.getReviewedCount());
            firstPass += nz(row.getFirstPassCount());
            rejected += nz(row.getRejectedCount());
        }

        StatsOverviewVO vo = new StatsOverviewVO();
        vo.setEntryDone(entryDone);
        vo.setTagCount(tagCount);
        vo.setFrameTotal(frameTotal);
        vo.setLabeledFrames(labeledFrames);
        vo.setEmptyFrames(emptyFrames);
        vo.setActiveMembers((long) memberIds.size());
        vo.setAvgEntriesPerMember(memberIds.isEmpty() ? 0D : round2(entryDone * 1D / memberIds.size()));
        vo.setAvgSecondsPerEntry(entryDone > 0 ? weightedSeconds / entryDone : 0L);
        vo.setWorkHours(workHours(dto).getTotalHours());
        vo.setFirstPassRate(reviewed > 0 ? percent(firstPass, reviewed) : null);
        vo.setRejectRate(reviewed > 0 ? percent(rejected, reviewed) : null);
        return vo;
    }

    @Override
    public List<StatsQualityVO> quality(StatsQueryDTO dto) {
        List<StatsQualityVO> rows = statsMapper.selectMemberQuality(prepare(dto));
        for (StatsQualityVO row : rows) {
            long reviewed = nz(row.getReviewedCount());
            row.setSubmittedCount(nz(row.getSubmittedCount()));
            row.setReviewedCount(reviewed);
            row.setPassedCount(nz(row.getPassedCount()));
            row.setRejectedCount(nz(row.getRejectedCount()));
            row.setFirstPassCount(nz(row.getFirstPassCount()));
            row.setReworkCount(nz(row.getReworkCount()));
            row.setFirstPassRate(reviewed > 0 ? percent(row.getFirstPassCount(), reviewed) : null);
            row.setRejectRate(reviewed > 0 ? percent(row.getRejectedCount(), reviewed) : null);
        }
        return rows;
    }

    @Override
    public long heartbeat(StatsHeartbeatDTO dto) {
        StatsHeartbeatDTO body = dto == null ? new StatsHeartbeatDTO() : dto;
        long userId = currentUserId();
        String nodeKey = (body.getNodeKey() == null || body.getNodeKey().isBlank())
            ? "label" : body.getNodeKey().trim();
        body.setNodeKey(nodeKey);
        int seconds = body.getSeconds() == null ? DEFAULT_HEARTBEAT_SECONDS : body.getSeconds();
        seconds = Math.min(Math.max(seconds, 1), MAX_HEARTBEAT_SECONDS);
        long taskId = body.getTaskId() == null ? 0L : body.getTaskId();
        String workDate = LocalDate.now().toString();

        statsMapper.upsertWorkSession(userId, body, workDate, seconds);
        Long total = statsMapper.selectWorkSessionSeconds(userId, taskId, nodeKey, workDate);
        return total == null ? seconds : total;
    }

    // ------------------------------------------------------------------

    private List<StatsMemberOutputVO> outputs(StatsQueryDTO dto, boolean sortByFrames) {
        List<StatsMemberOutputVO> rows = statsMapper.selectMemberOutput(prepare(dto), labelOnly(prepare(dto)));
        for (StatsMemberOutputVO row : rows) {
            row.setTagCount(nz(row.getTagCount()));
            row.setTotalFrames(nz(row.getTotalFrames()));
            row.setLabeledFrames(nz(row.getLabeledFrames()));
            row.setEmptyFrames(nz(row.getEmptyFrames()));
            row.setTagPerFrame(ratio(row.getTagCount(), row.getTotalFrames()));
        }
        if (sortByFrames) {
            rows.sort(Comparator.comparing(StatsMemberOutputVO::getTotalFrames).reversed());
        }
        return rows;
    }

    private StatsQueryDTO prepare(StatsQueryDTO dto) {
        StatsQueryDTO q = dto == null ? new StatsQueryDTO() : dto;
        LocalDate end = parseDate(q.getEndDate(), LocalDate.now());
        LocalDate start = parseDate(q.getStartDate(), end.minusDays(DEFAULT_RANGE_DAYS - 1L));
        if (start.isAfter(end)) {
            throw new ServiceException("开始日期不能晚于结束日期");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_RANGE_DAYS) {
            throw new ServiceException("统计时间跨度不能超过 " + MAX_RANGE_DAYS + " 天");
        }
        q.setStartDate(start.toString());
        q.setEndDate(end.toString());
        int limit = q.getLimit() == null ? DEFAULT_LIMIT : q.getLimit();
        q.setLimit(Math.min(Math.max(limit, 1), MAX_LIMIT));
        if (q.getNodeKey() != null && q.getNodeKey().isBlank()) {
            q.setNodeKey(null);
        }
        return q;
    }

    /**
     * 产出类指标（标签数/帧数）是否只统计标注类节点。
     *
     * <p>标签与帧是「标注动作」的产物，只归属标注节点。为避免在审核类节点下
     * 把同一批标注对象重复计入，未指定节点（默认）或选中审核类节点时，
     * 产出统一回落到标注节点；仅当明确选中某个标注类节点时才按该节点统计。</p>
     */
    private boolean labelOnly(StatsQueryDTO q) {
        if (q.getNodeKey() == null) {
            return true;
        }
        String nodeType = nodeTypeOf(q.getNodeKey());
        return !"label".equals(nodeType);
    }

    /** 成员下拉放宽到近 90 天，避免默认 7 天内无人产出时下拉为空。 */
    private StatsQueryDTO memberOptionQuery(StatsQueryDTO q) {
        StatsQueryDTO copy = new StatsQueryDTO();
        copy.setProjectId(q.getProjectId());
        copy.setTaskId(q.getTaskId());
        copy.setTeamId(q.getTeamId());
        copy.setStartDate(LocalDate.now().minusDays(89).toString());
        copy.setEndDate(LocalDate.now().toString());
        copy.setLimit(500);
        return copy;
    }

    private LocalDate parseDate(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new ServiceException("日期格式应为 yyyy-MM-dd：" + value);
        }
    }

    private List<String> dateRange(StatsQueryDTO q) {
        LocalDate start = LocalDate.parse(q.getStartDate());
        LocalDate end = LocalDate.parse(q.getEndDate());
        List<String> dates = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            dates.add(d.toString());
        }
        return dates;
    }

    private StatsDailyMatrixVO buildMatrix(StatsQueryDTO q, List<StatsDailyCellVO> cells,
        String metric, String unit) {
        List<String> dates = dateRange(q);
        Map<Long, StatsDailyRowVO> rowMap = new LinkedHashMap<>();
        for (StatsDailyCellVO cell : cells) {
            StatsDailyRowVO row = rowMap.computeIfAbsent(cell.getMemberId(), id -> {
                StatsDailyRowVO r = new StatsDailyRowVO();
                r.setMemberId(id);
                r.setMemberName(cell.getMemberName());
                r.setValues(new LinkedHashMap<>());
                r.setTotal(0L);
                return r;
            });
            long value = nz(cell.getValue());
            row.getValues().merge(cell.getDay(), value, Long::sum);
            row.setTotal(row.getTotal() + value);
        }

        List<Long> columnTotals = new ArrayList<>();
        for (String date : dates) {
            long columnTotal = 0L;
            for (StatsDailyRowVO row : rowMap.values()) {
                columnTotal += row.getValues().getOrDefault(date, 0L);
            }
            columnTotals.add(columnTotal);
        }

        List<StatsDailyRowVO> rows = new ArrayList<>(rowMap.values());
        for (StatsDailyRowVO row : rows) {
            Map<String, Long> filled = new LinkedHashMap<>();
            for (String date : dates) {
                filled.put(date, row.getValues().getOrDefault(date, 0L));
            }
            row.setValues(filled);
        }
        rows.sort(Comparator.comparing(StatsDailyRowVO::getTotal).reversed());

        StatsDailyMatrixVO vo = new StatsDailyMatrixVO();
        vo.setMetric(metric);
        vo.setUnit(unit);
        vo.setDates(dates);
        vo.setRows(rows);
        vo.setColumnTotals(columnTotals);
        vo.setTotal(columnTotals.stream().mapToLong(Long::longValue).sum());
        return vo;
    }

    private Map<Long, Map<String, Long>> groupByMember(List<StatsWorkCellVO> cells,
        Map<Long, String> names) {
        Map<Long, Map<String, Long>> result = new HashMap<>();
        for (StatsWorkCellVO cell : cells) {
            if (cell.getMemberId() == null) {
                continue;
            }
            names.putIfAbsent(cell.getMemberId(), cell.getMemberName());
            result.computeIfAbsent(cell.getMemberId(), id -> new HashMap<>())
                .merge(cell.getDay(), nz(cell.getSeconds()), Long::sum);
        }
        return result;
    }

    private String resolveDefaultNodeKey(List<StatsNodeOptionVO> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return "label";
        }
        for (StatsNodeOptionVO node : nodes) {
            if ("label".equals(node.getNodeType())) {
                return node.getNodeKey();
            }
        }
        return nodes.get(0).getNodeKey();
    }

    /** 根据节点 key 查其类型（label/review/...）；未知时按标注类处理。 */
    private String nodeTypeOf(String nodeKey) {
        if (nodeKey == null) {
            return "label";
        }
        for (StatsNodeOptionVO node : statsMapper.selectNodeOptions(new StatsQueryDTO())) {
            if (nodeKey.equals(node.getNodeKey())) {
                return node.getNodeType() == null ? "label" : node.getNodeType();
            }
        }
        return "label";
    }

    private long sum(Map<String, Long> values) {
        long total = 0L;
        for (Long v : values.values()) {
            total += nz(v);
        }
        return total;
    }

    private long nz(Long value) {
        return value == null ? 0L : value;
    }

    private Double ratio(long numerator, long denominator) {
        return denominator <= 0 ? 0D : round2(numerator * 1D / denominator);
    }

    private Double percent(long numerator, long denominator) {
        return denominator <= 0 ? null : round2(numerator * 100D / denominator);
    }

    private double round2(double value) {
        return Math.round(value * 100D) / 100D;
    }
}
