package org.dromara.label.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.label.common.MediaType;
import org.dromara.label.mapper.LabelCommonMapper;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 标注平台领域服务的公共基类。
 *
 * <p>抽取各领域服务共用的 JDBC 查询、SQL 拼接、类型转换、JSON 序列化、当前用户、
 * 操作日志、消息通知、团队绑定、字典ID解析等能力，
 * 供 {@link ProjectService}、{@link TaskService}、{@link AnnotationService}、
 * {@link ReviewService}、{@link TeamService}、{@link DashboardService}、
 * {@link ExportService}、{@link NotificationService}、{@link QcService}、
 * {@link SupportService} 等子类复用，避免重复造轮子。</p>
 */
public abstract class BaseService {

  /** 当前登录用户 ID（MVP 阶段固定为管理员，接入登录态后替换）。 */
  protected static final long CURRENT_USER_ID = 1L;

  @Autowired
  protected ObjectMapper objectMapper;

  /** 公共 SQL 数据访问（sys 表、团队、项目关联等支撑查询）。 */
  @Autowired
  protected LabelCommonMapper labelCommonMapper;

  // ---------------- 通用小工具 ----------------

  /** 跨状态作业计数（看板待办复用）。 */
  protected long countJobs(String status) {
    return labelCommonMapper.countJobs(status);
  }

  /**
   * 发号锁：编号由「查最大 ID + 1」得出（两次查询之间无隔离保证），
   * 并发调用会取到相同值并撞上编号唯一索引，故在同一进程内串行取号。
   */
  private static final Object CODE_SEQUENCE_LOCK = new Object();

  protected long nextCode(String tableName) {
    synchronized (CODE_SEQUENCE_LOCK) {
      return labelCommonMapper.nextId(tableName);
    }
  }

  protected Object firstPresent(Object... values) {
    for (Object value : values) {
      if (value != null && !String.valueOf(value).isBlank()) {
        return value;
      }
    }
    return null;
  }

  // ---------------- JSON / 类型转换 ----------------

  protected String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new ServiceException("JSON 序列化失败");
    }
  }

  protected Object parseJson(Object value) {
    if (value == null || String.valueOf(value).isBlank()) {
      return Map.of();
    }
    try {
      return objectMapper.readValue(String.valueOf(value), Object.class);
    } catch (JsonProcessingException e) {
      return Map.of("raw", String.valueOf(value));
    }
  }

  protected String nestedKind(Object value) {
    if (!(value instanceof Map<?, ?> map)) {
      return "";
    }
    Object kind = map.get("kind");
    return kind == null ? "" : String.valueOf(kind);
  }

  protected Long asLong(Object value) {
    if (value == null || String.valueOf(value).isBlank()) {
      return null;
    }
    if (value instanceof Number number) {
      return number.longValue();
    }
    return Long.valueOf(String.valueOf(value));
  }

  protected Number asNumber(Object value) {
    if (value instanceof Number number) {
      return number;
    }
    if (value == null || String.valueOf(value).isBlank()) {
      return 0;
    }
    return Double.valueOf(String.valueOf(value));
  }

  protected int asInt(Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value == null || String.valueOf(value).isBlank()) {
      return 0;
    }
    return Integer.parseInt(String.valueOf(value));
  }

  protected long parseFileSize(Object value) {
    if (value instanceof Number number) {
      return number.longValue();
    }
    String text = String.valueOf(value).trim().toUpperCase(Locale.ROOT);
    try {
      if (text.endsWith("GB")) {
        return (long) (Double.parseDouble(text.replace("GB", "").trim()) * 1024 * 1024 * 1024);
      }
      if (text.endsWith("MB")) {
        return (long) (Double.parseDouble(text.replace("MB", "").trim()) * 1024 * 1024);
      }
      if (text.endsWith("KB")) {
        return (long) (Double.parseDouble(text.replace("KB", "").trim()) * 1024);
      }
      return Long.parseLong(text);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  protected String stringValue(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  protected String sanitizeAssetFileName(String fileName) {
    String safe = fileName == null || fileName.isBlank() ? "upload-" + System.currentTimeMillis() : fileName.trim();
    safe = safe.replaceAll("[/\\\\:*?\"<>|]", "_");
    return safe.length() > 200 ? safe.substring(safe.length() - 200) : safe;
  }

  /**
   * 清洗上传文件的相对路径：保留目录层级（分隔符统一为 /），逐段去除非法字符并防目录穿越。
   * 返回空串表示路径非法（为空、绝对路径或包含 .. 穿越）。
   */
  protected String sanitizeRelativePath(String path) {
    String raw = path == null ? "" : path.trim().replace('\\', '/');
    if (raw.isEmpty()) {
      return "";
    }
    Path rel;
    try {
      rel = Path.of(raw).normalize();
    } catch (InvalidPathException e) {
      return "";
    }
    if (rel.isAbsolute() || rel.getNameCount() == 0 || rel.startsWith("..")) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (Path part : rel) {
      String seg = part.toString().replaceAll("[*?\"<>|:]", "_").trim();
      if (seg.isEmpty() || ".".equals(seg)) {
        continue;
      }
      if (sb.length() > 0) {
        sb.append('/');
      }
      sb.append(seg);
    }
    String safe = sb.toString();
    return safe.length() > 200 ? safe.substring(safe.length() - 200) : safe;
  }

  // ---------------- 当前用户 / 审计 / 通知 ----------------

  /** 当前登录用户ID；无登录上下文（后台任务等）时回退默认用户1。
   *  <p>注意：线程池异步任务里 LoginHelper 可能<b>返回 null 而不抛异常</b>（工作线程没有
   *  Sa-Token 登录上下文），只 catch 异常防不住 —— null 在 long 解包处就是 NPE
   *  （nuScenes 导入后自动解析已在 persistSensorCalib 踩过），这里必须双保险。</p> */
  protected Long currentUserId() {
    try {
      Long userId = LoginHelper.getUserId();
      return userId != null ? userId : 1L;
    } catch (Exception e) {
      return 1L;
    }
  }

  /**
   * 写入操作日志（已停用）。操作日志统一由 Controller 层 @Log 注解异步写入 sys_oper_log，
   * 旧表 sys_operation_log 不再写入；保留方法签名以兼容既有调用点，避免改动各业务实现。
   */
  protected void writeOperationLog(String action, String targetType, Long targetId, Map<String, ?> detail) {
    // no-op
  }

  /** 创建站内消息通知；userId 为空时忽略。 */
  protected void createNotification(Long userId, String title, String content, String bizType, Long bizId) {
    if (userId == null) {
      return;
    }
    labelCommonMapper.insertNotification(userId, title, content, bizType, bizId);
  }

  // ---------------- 字典 / 归属解析 ----------------

  protected Long findUserId(String name, Long fallbackId) {
    if (name == null || name.isBlank()) {
      return fallbackId;
    }
    Long id = labelCommonMapper.findUserIdByName(name);
    return id == null ? fallbackId : id;
  }

  /** 校验用户是否拥有指定角色（sys_role.role_key），不满足时抛出业务异常。 */
  protected void assertUserHasRole(Long userId, String roleKey, String roleName) {
    if (userId == null) {
      return;
    }
    if (labelCommonMapper.countUserRole(userId, roleKey) == 0) {
      throw new ServiceException("所选" + roleName + "必须具有「" + roleName + "」角色，请重新选择");
    }
  }

  protected Long findProjectId(String projectName, Long fallbackId) {
    if (projectName == null || projectName.isBlank()) {
      return fallbackId;
    }
    Long id = labelCommonMapper.findProjectIdByName(projectName);
    return id == null ? fallbackId : id;
  }

  protected Long findTaskId(String taskName) {
    if (taskName == null || taskName.isBlank()) {
      return null;
    }
    return labelCommonMapper.findTaskIdByName(taskName);
  }

  protected Long findAssigneeId(Long jobId) {
    return labelCommonMapper.findAssigneeIdByJobId(jobId);
  }

  protected Long findReviewerId(Long jobId) {
    Long reviewerId = labelCommonMapper.findReviewerIdByJobId(jobId);
    return reviewerId == null ? currentUserId() : reviewerId;
  }

  protected Long firstClassSetId(Long projectId) {
    return labelCommonMapper.firstClassSetId(projectId);
  }

  /** 默认批次创建锁：多文件并发上传时，避免同一项目被重复创建出多个「默认批次」。 */
  private static final Object DEFAULT_BATCH_LOCK = new Object();

  /** 项目最近一个资产批次；不存在时自动创建一个 upload/parsed 批次。 */
  protected Long firstBatchId(Long projectId) {
    Long batchId = labelCommonMapper.lastAssetBatchId(projectId);
    if (batchId != null) {
      return batchId;
    }
    synchronized (DEFAULT_BATCH_LOCK) {
      // 双检：并发请求绝大多数会在锁外命中已有批次，只有首个请求真正创建
      Long created = labelCommonMapper.lastAssetBatchId(projectId);
      if (created != null) {
        return created;
      }
      String batchNo = "BATCH-" + nextCode("label_asset_batch");
      labelCommonMapper.insertAssetBatch(projectId, batchNo, "默认批次", currentUserId());
      return labelCommonMapper.selectBatchIdByNo(projectId, batchNo);
    }
  }

  /**
   * 解析导入的目标批次：显式指定时校验其存在且属于该项目；
   * 未指定时回退到项目最近一个批次（不存在则自动创建），兼容旧版上传入口。
   */
  protected Long resolveBatchId(Long projectId, Long batchId) {
    if (batchId == null) {
      return firstBatchId(projectId);
    }
    Long ownerProjectId = labelCommonMapper.selectBatchProjectId(batchId);
    if (ownerProjectId == null || !ownerProjectId.equals(projectId)) {
      throw new ServiceException("所选数据批次不存在或不属于该项目");
    }
    return batchId;
  }

  /** 作业的首个资产：优先取 job_asset 排序首位，退化到项目内同类型资产首个。 */
  protected Long firstAssetId(Long jobId) {
    Long assetId = labelCommonMapper.assetIdByJobAsset(jobId);
    if (assetId != null) {
      return assetId;
    }
    Long projectId = labelCommonMapper.selectJobProjectId(jobId);
    if (projectId == null) {
      return null;
    }
    String dataType = labelCommonMapper.selectJobDataType(jobId);
    if (dataType == null || dataType.isBlank()) {
      dataType = MediaType.IMAGE;
    }
    return labelCommonMapper.firstAssetIdOfProject(projectId, dataType);
  }

  // ---------------- 团队绑定 ----------------

  protected Long findOrCreateTeam(String teamName) {
    if (teamName == null || teamName.isBlank()) {
      return null;
    }
    Long id = labelCommonMapper.teamIdByName(teamName);
    if (id != null) {
      return id;
    }
    labelCommonMapper.insertTeam(teamName, currentUserId());
    return labelCommonMapper.teamIdByName(teamName);
  }

  /**
   * 解析请求中的团队ID列表：兼容 JSON 数组与 "1,2,3" 字符串。
   */
  protected List<Long> parseTeamIds(Object raw) {
    List<Long> result = new ArrayList<>();
    if (raw == null) {
      return result;
    }
    if (raw instanceof Collection<?> collection) {
      for (Object o : collection) {
        Long v = asLong(o);
        if (v != null) {
          result.add(v);
        }
      }
    } else {
      String text = String.valueOf(raw);
      if (!text.isBlank()) {
        for (String part : text.split(",")) {
          Long v = asLong(part.trim());
          if (v != null) {
            result.add(v);
          }
        }
      }
    }
    return result.stream().distinct().collect(java.util.stream.Collectors.toList());
  }

  /** 校验并返回实际存在(未删除)的团队ID，忽略已删除/不存在的项。 */
  protected List<Long> validTeamIds(List<Long> teamIds) {
    if (teamIds == null || teamIds.isEmpty()) {
      return List.of();
    }
    return labelCommonMapper.selectValidTeamIds(teamIds);
  }

  /**
   * 归并项目保存时的队伍集合：主队伍(teamName 解析)优先置于首位，其余队伍取自请求 teamIds
   * （兼容 JSON 数组与逗号分隔字符串），忽略已删除的团队，结果保序去重。
   */
  protected List<Long> resolveProjectTeams(Map<String, Object> body, Long mainTeamId) {
    List<Long> result = new ArrayList<>();
    if (mainTeamId != null) {
      result.add(mainTeamId);
    }
    for (Long teamId : validTeamIds(parseTeamIds(body.get("teamIds")))) {
      if (!result.contains(teamId)) {
        result.add(teamId);
      }
    }
    return result;
  }

  /** 全量替换项目绑定的队伍（先清空关联表再写入），并同步 label_project.team_id 主队伍。 */
  protected void syncProjectTeams(Long projectId, List<Long> teamIds) {
    labelCommonMapper.deleteProjectTeams(projectId);
    if (teamIds != null) {
      for (Long teamId : teamIds) {
        if (teamId == null) {
          continue;
        }
        labelCommonMapper.insertProjectTeam(projectId, teamId, currentUserId());
      }
    }
    Long primaryTeamId = (teamIds == null || teamIds.isEmpty()) ? null : teamIds.get(0);
    labelCommonMapper.updateProjectPrimaryTeam(projectId, primaryTeamId, currentUserId());
  }

  /** 为单条项目补充 teamIds/teamNames 多队字段。 */
  protected void attachProjectTeams(Map<String, Object> row) {
    if (row == null) {
      return;
    }
    attachProjectTeams(List.of(row));
  }

  /** 为项目列表批量补充 teamIds/teamNames 多队字段。 */
  protected void attachProjectTeams(List<Map<String, Object>> rows) {
    if (rows == null || rows.isEmpty()) {
      return;
    }
    List<Long> ids =
        rows.stream().map(r -> asLong(r.get("id"))).filter(Objects::nonNull).distinct().toList();
    if (ids.isEmpty()) {
      return;
    }
    List<Map<String, Object>> rels = labelCommonMapper.selectProjectTeams(ids);
    Map<Long, List<Map<String, Object>>> byProject =
        rels.stream()
            .collect(
                java.util.stream.Collectors.groupingBy(
                    r -> asLong(r.get("projectId")), java.util.stream.Collectors.toList()));
    for (Map<String, Object> row : rows) {
      List<Map<String, Object>> rel = byProject.getOrDefault(asLong(row.get("id")), List.of());
      row.put(
          "teamIds",
          rel.stream().map(r -> asLong(r.get("teamId"))).filter(Objects::nonNull).toList());
      row.put(
          "teamNames",
          rel.stream()
              .map(r -> String.valueOf(r.get("teamName")))
              .filter(s -> !s.isBlank())
              .toList());
    }
  }

  /** 确保团队成员存在（幂等 upsert）。 */
  protected void ensureTeamMember(Long teamId, Long userId, String role) {
    if (teamId == null || userId == null) {
      return;
    }
    labelCommonMapper.upsertTeamMember(teamId, userId, role, currentUserId());
  }
}
