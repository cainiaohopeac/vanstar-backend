package org.dromara.label.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.dromara.label.common.AnnotationObjectType;
import org.dromara.label.common.AnnotationStatus;
import org.dromara.label.common.LabelEntryHistoryAction;
import org.dromara.label.common.LabelEntryStatus;
import org.dromara.label.common.LabelJobStatus;
import org.dromara.label.common.LabelReviewRecord;
import org.dromara.label.common.LabelReviewStatus;
import org.dromara.label.common.MediaType;
import org.dromara.label.common.PageResult;
import org.dromara.label.dto.AnnotationFrameDTO;
import org.dromara.label.dto.AnnotationObjectDTO;
import org.dromara.label.dto.AnnotationSaveDTO;
import org.dromara.label.dto.AssetReviewContextDTO;
import org.dromara.label.dto.ObjectCommentDTO;
import org.dromara.label.dto.ObjectTrackDTO;
import org.dromara.label.entity.LabelAnnotationIdRow;
import org.dromara.label.entity.LabelAnnotationObjectRow;
import org.dromara.label.entity.LabelNuscenesAssetMap;
import org.dromara.label.entity.LabelObjectComment;
import org.dromara.label.mapper.AnnotationMapper;
import org.dromara.label.mapper.NuscenesMapper;
import org.dromara.label.mapper.ProjectMapper;
import org.dromara.label.mapper.ReviewMapper;
import org.dromara.label.mapper.TaskMapper;
import org.dromara.label.service.AnnotationService;
import org.dromara.label.service.BaseService;
import org.dromara.label.service.EntryHistoryService;
import org.dromara.label.service.TaskService;
import org.dromara.label.service.WorkflowService;
import org.dromara.label.vo.AnnotationSaveResultVO;
import org.dromara.label.vo.AnnotationViewVO;
import org.dromara.label.vo.AssetAnnotationVO;
import org.dromara.label.vo.CalibVO;
import org.dromara.label.vo.ClassAttributeVO;
import org.dromara.label.vo.JobVO;
import org.dromara.label.vo.ObjectCommentVO;
import org.dromara.label.vo.WorkAssetVO;
import org.dromara.label.vo.WorkClassVO;
import org.dromara.label.vo.WorkObjectVO;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 标注域服务：标注工作台（作业标注取数/保存标注/资产标注）、标注员视角作业列表。
 * SQL 统一收敛至 AnnotationMapper.xml。
 */
@Service
public class AnnotationServiceImpl extends BaseService implements AnnotationService {

  private static final org.slf4j.Logger REVIEWER_LOG =
      org.slf4j.LoggerFactory.getLogger(AnnotationServiceImpl.class);

  @Autowired
  private AnnotationMapper annotationMapper;

  /** 项目域数据访问（类别属性查询：selectClassAttributes）。 */
  @Autowired
  private ProjectMapper projectMapper;

  @Autowired
  private TaskService taskService;

  /** 审核域数据访问（仅用于审核员修订模式的鉴权与审计写入），
   * 复用 ReviewMapper 现有方法，零新增 mapper（除 selectAssetReviewContext）。 */
  @Autowired
  private ReviewMapper reviewMapper;

  /** 任务域数据访问（查询任务的预标注引入开关）。 */
  @Autowired
  private TaskMapper taskMapper;

  /** nuScenes 域数据访问（点云资产的相机参考图映射）。 */
  @Autowired
  private NuscenesMapper nuscenesMapper;

  /** 条目操作历史。 */
  @Autowired
  private EntryHistoryService entryHistoryService;

  /** 工作流域：解析当前作业所处节点，取该节点配置的批注类型下发给工作台 */
  @Autowired
  private WorkflowService workflowService;

  /** 预标注引入开关的开启值 */
  private static final Integer PRE_ANNOTATION_ENABLED_ON = 1;

  /** 批注描述最大长度（审核员填写的补充说明） */
  private static final int MAX_COMMENT_CONTENT_LENGTH = 200;

  /** 批注类型最大长度（与 label_object_comment.comment_type 列宽对齐） */
  private static final int MAX_COMMENT_TYPE_LENGTH = 32;

  /**
   * 批量写入标注对象时每条 INSERT 携带的行数。
   *
   * <p>500 是权衡结果：太小则往返次数多（失去批量意义），太大则可能撑爆
   * MySQL 的 {@code max_allowed_packet}（每个对象都带 geometry/attr 的 JSON，单行可达数 KB）。
   */
  private static final int OBJECT_BATCH_SIZE = 500;

  // ---------------- 标注 ----------------

  /** 标注工作台：返回作业信息、资产列表、类别体系与已保存的标注对象。 */
  @Override
  public AnnotationViewVO annotation(Long jobId) {
    JobVO job = taskService.job(jobId);
    // 只返回该作业「已领取」的资产（label_job_asset）。
    // 这里原先在为空时兜底查询整个 project 的同类型资产，导致「只领了 2 个条目却展示全部条目」，
    // 属于越权展示，已去掉：资产范围由领取流程写入 label_job_asset 决定。
    List<WorkAssetVO> assets = annotationMapper.selectJobAssets(jobId);
    AnnotationViewVO result = new AnnotationViewVO();
    result.setJobId(jobId);
    result.setJob(job);
    result.setMediaType(job.getDataType());
    // 「数据明细」面板：任务名称/任务类型/批次名称/条目数量/条目类型
    result.setTaskInfo(annotationMapper.selectJobTaskInfo(jobId));
    // 点云作业：为每个 bin 关联同帧相机参考图
    if (MediaType.POINT_CLOUD.equals(job.getDataType())) {
      // 1. nuScenes 元数据映射（bin 与相机图不同名，按 sample 同帧关系关联）优先
      attachNuscenesRefImages(assets);
      // 2. 同名规则兜底（bin 与相机图同名、仅扩展名不同的场景），已有映射的资产不覆盖
      attachRefImages(assets, annotationMapper.selectJobReferenceImages(jobId));
    }
    result.setAssets(assets);
    result.setActiveAsset(assets.isEmpty() ? null : assets.get(0));
    result.setClasses(annotationMapper.selectJobClasses(jobId));
    // 类别属性：随标注接口下发（attr_options 反解析为原始值列表），
    // 供画布标签属性行、成框后属性赋值表单与 OCR 文本属性定位使用
    if (result.getClasses() != null) {
      for (WorkClassVO cls : result.getClasses()) {
        List<ClassAttributeVO> attrs = projectMapper.selectClassAttributes(cls.getClassId());
        if (attrs != null) {
          for (ClassAttributeVO attr : attrs) {
            Object options = parseJson(attr.getAttrOptionsJson());
            attr.setAttrOptions(options instanceof List<?> items ? new ArrayList<>(items) : List.of());
            attr.setAttrOptionsJson(null);
          }
        }
        cls.setAttributes(attrs == null ? List.of() : attrs);
      }
    }
    result.setObjects(annotationMapper.selectAnnotationObjects(jobId));
    // 逐帧（关键帧插值）：返回对象轨迹的存在范围与逐帧控制元数据，
    // 中间帧不落库，由前端按 trackToken + 关键帧几何实时插值渲染。
    result.setTracks(annotationMapper.selectJobTracks(jobId));
    // 整图批注回显：标注员「问题批注」随 DTO 整包存档在 annotation_json，此处取回
    attachComment(result, jobId);
    // 帧级全局属性值回显：随 DTO 整包存档在 annotation_json $.globalAttrs，此处取回
    attachGlobalAttrs(result, jobId);
    // 批注类型（错误内容下拉项）：按「任务当前所处节点」的项目配置下发，
    // 前端只在批注环节显示下拉，标注/原始数据环节保留原有纯文本批注
    attachCommentTypes(result, job);
    // 预标注引入：任务开启时，把尚无正式标注资产的预标注框一并返回
    //（前端注入画布：可删改，首次保存即转正为正式标注）
    attachPreAnnotations(result, job);
    return result;
  }

  /**
   * 下发生效的批注类型：作业 → 任务 → 项目，再按任务当前节点取该审核节点配置的类型。
   * 解析失败（缺任务/项目）时返回默认五类，保证工作台始终有可选错误内容。
   */
  private void attachCommentTypes(AnnotationViewVO result, JobVO job) {
    Long taskId = job == null ? null : job.getTaskId();
    Long projectId = workflowService.projectIdOfTask(taskId);
    String nodeKey = taskId == null ? null : workflowService.effectiveNodeKey(taskId);
    result.setCommentTypes(workflowService.commentTypesOfNode(projectId, nodeKey));
  }

  /** 取回当前激活资产最近一次存档的整图批注（无存档时保持为空）。 */
  private void attachComment(AnnotationViewVO result, Long jobId) {
    WorkAssetVO active = result.getActiveAsset();
    if (active == null || active.getAssetId() == null) {
      return;
    }
    result.setComment(annotationMapper.selectAnnotationComment(jobId, active.getAssetId()));
  }

  /** 取回当前激活资产最近一次存档的帧级全局属性值（无存档或解析失败时保持为空）。 */
  @Override
  public Map<String, Object> assetGlobalAttrs(Long assetId) {
    Long jobId = annotationMapper.selectJobIdByAsset(assetId);
    if (jobId == null) {
      return Map.of();
    }
    String json = annotationMapper.selectAnnotationGlobalAttrs(jobId, assetId);
    if (json == null || json.isBlank() || "null".equals(json)) {
      return Map.of();
    }
    Object parsed = parseJson(json);
    return parsed instanceof Map<?, ?> map ? new LinkedHashMap<>(castMap(map)) : Map.of();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> castMap(Map<?, ?> map) {
    return (Map<String, Object>) map;
  }

  private void attachGlobalAttrs(AnnotationViewVO result, Long jobId) {
    WorkAssetVO active = result.getActiveAsset();
    if (active == null || active.getAssetId() == null) {
      return;
    }
    String json = annotationMapper.selectAnnotationGlobalAttrs(jobId, active.getAssetId());
    if (json == null || json.isBlank() || "null".equals(json)) {
      return;
    }
    Object parsed = parseJson(json);
    if (parsed instanceof Map<?, ?> map) {
      result.setGlobalAttrs(map);
    }
  }

  /**
   * 挂载 nuScenes 相机参考图：按 label_nuscenes_asset_map 的同帧映射，
   * 为点云资产补充参考图（nuScenes 的 bin 与相机图文件名不同，同名规则无法匹配）。
   */
  private void attachNuscenesRefImages(List<WorkAssetVO> assets) {
    if (assets == null || assets.isEmpty()) {
      return;
    }
    // 1. 收集点云资产ID，批量查映射
    List<Long> assetIds = assets.stream().map(WorkAssetVO::getAssetId).filter(Objects::nonNull).toList();
    if (assetIds.isEmpty()) {
      return;
    }
    Map<Long, List<LabelNuscenesAssetMap>> byAssetId = new HashMap<>();
    // 同一（点云资产, 相机通道）只取一条映射：历史解析数据里同一 sample 挂了相邻帧的
    // 相机 sample_data 时写过同通道两行，不去重的话相机参考图整批翻倍。
    // SQL 按 asset_id, camera_channel, id 排序 → 保留首条 = id 最小 = 与点云帧同拍的那条
    Set<String> seenMapKeys = new HashSet<>();
    for (LabelNuscenesAssetMap map : nuscenesMapper.selectMapsByAssetIds(assetIds)) {
      if (!seenMapKeys.add(map.getAssetId() + "#" + map.getCameraChannel())) {
        continue;
      }
      byAssetId.computeIfAbsent(map.getAssetId(), k -> new ArrayList<>()).add(map);
    }
    // 2. 逐资产把映射的相机图转为参考图 VO
    for (WorkAssetVO asset : assets) {
      List<LabelNuscenesAssetMap> maps = byAssetId.get(asset.getAssetId());
      if (maps == null || maps.isEmpty()) {
        continue;
      }
      // 该帧自车位姿（同帧多通道共享，取首条即可）：
      // 前端据此把各帧点云从自车/雷达坐标系统一到全局坐标系，避免切帧时背景漂移
      asset.setEgoTranslation(doubleList(maps.get(0).getEgoTranslation()));
      asset.setEgoRotation(doubleList(maps.get(0).getEgoRotation()));
      List<WorkAssetVO> refs = new ArrayList<>();
      for (LabelNuscenesAssetMap map : maps) {
        if (map.getCameraFileUrl() == null || map.getCameraFileUrl().isBlank()) {
          continue;
        }
        WorkAssetVO ref = new WorkAssetVO();
        ref.setAssetId(map.getCameraAssetId());
        ref.setFileName(map.getCameraFileName());
        ref.setFileUrl(map.getCameraFileUrl());
        ref.setThumbnailUrl(map.getCameraFileUrl());
        ref.setMediaType(MediaType.IMAGE);
        // 相机标定（内外参 + 图片尺寸）：供前端投影联动
        ref.setCalib(cameraCalibOf(map));
        refs.add(ref);
      }
      if (!refs.isEmpty()) {
        // 点云资产自身挂 LIDAR 外参（同帧 5 通道共享，取首条即可）
        asset.setCalib(lidarCalibOf(maps.get(0)));
        asset.setRefImages(refs);
      }
    }
  }

  /** 从映射行提取相机标定 VO（内参 + 外参 + 图片尺寸）。 */
  private CalibVO cameraCalibOf(LabelNuscenesAssetMap map) {
    CalibVO calib = new CalibVO();
    calib.setIntrinsic(doubleList(map.getCameraIntrinsic()));
    calib.setRotation(doubleList(map.getCameraRotation()));
    calib.setTranslation(doubleList(map.getCameraTranslation()));
    calib.setWidth(map.getCameraWidth());
    calib.setHeight(map.getCameraHeight());
    return calib;
  }

  /** 从映射行提取 LIDAR 外参 VO（点云资产的投影起点坐标系）。 */
  private CalibVO lidarCalibOf(LabelNuscenesAssetMap map) {
    CalibVO calib = new CalibVO();
    calib.setRotation(doubleList(map.getLidarRotation()));
    calib.setTranslation(doubleList(map.getLidarTranslation()));
    return calib;
  }

  /** 解析标定 JSON 字符串（形如 [1.0,2.0,...]）为 double 列表。 */
  private List<Double> doubleList(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    Object parsed = parseJson(json);
    if (!(parsed instanceof List<?> list)) {
      return null;
    }
    List<Double> result = new ArrayList<>();
    for (Object item : list) {
      result.add(item instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(item)));
    }
    return result;
  }

  /**
   * 按"文件名去扩展名"匹配参考图：bin 与相机图同名（仅扩展名不同），依此建立关联。
   * 已有 nuScenes 映射参考图的资产保持不变（映射优先）。
   */
  private void attachRefImages(List<WorkAssetVO> assets, List<WorkAssetVO> refs) {
    if (assets == null || refs == null || refs.isEmpty()) {
      return;
    }
    Map<String, List<WorkAssetVO>> byStem = new HashMap<>();
    // 同一存储路径（fileUrl）只取一条资产：数据被重复上传时会产生多条**同 fileUrl** 的
    // 资产行，不去重的话每帧每通道配上 N 张相同的相机图（参考图成倍膨胀）。
    // 注意不能按文件名（stem）去重——自采数据集 5 路相机**同名、仅目录不同**，
    // 按名去重会把 5 路压成 1 路；fileUrl 含通道目录，天然区分通道
    Set<String> seenRefUrls = new HashSet<>();
    for (WorkAssetVO ref : refs) {
      if (!seenRefUrls.add(ref.getFileUrl())) {
        continue;
      }
      byStem.computeIfAbsent(stem(ref.getFileName()), k -> new ArrayList<>()).add(ref);
    }
    for (WorkAssetVO asset : assets) {
      List<WorkAssetVO> group = byStem.get(stem(asset.getFileName()));
      if (asset.getRefImages() == null || asset.getRefImages().isEmpty()) {
        asset.setRefImages(group == null ? List.of() : group);
      }
    }
  }

  /**
   * 预标注引入挂载：任务开关开启时查询作业资产的预标注对象（仅尚无正式标注的资产）。
   */
  private void attachPreAnnotations(AnnotationViewVO result, JobVO job) {
    // 1. 任务未开启引入则直接返回
    if (job == null || job.getTaskId() == null
        || !PRE_ANNOTATION_ENABLED_ON.equals(taskMapper.selectTaskPreAnnotationEnabled(job.getTaskId()))) {
      return;
    }
    // 2. 查询预标注对象（SQL 已过滤「已有正式标注」的资产，避免重复注入）。
    // 这里必须是<b>作业维度的全量查询</b>，不能收敛到当前激活资产：
    // 标注页一次请求要拿到整个作业（多帧 / 多条数据）的全部对象，
    // 逐帧渲染时按 WorkObjectVO.assetId 区分归属；而切帧是前端本地切换、
    // 不会重新请求后端——只查当前帧的话，其余帧就永远拿不到自己的预标注了。
    List<WorkObjectVO> preAnnotations = annotationMapper.selectJobPreAnnotationObjects(result.getJobId());
    if (preAnnotations != null && !preAnnotations.isEmpty()) {
      result.setPreAnnotationEnabled(true);
      result.setPreAnnotationObjects(preAnnotations);
    }
  }

  private String stem(String fileName) {
    if (fileName == null) {
      return "";
    }
    int idx = fileName.lastIndexOf('.');
    return (idx > 0 ? fileName.substring(0, idx) : fileName).toLowerCase();
  }

  /** 保存标注：status 为 pending_review 表示提交审核，其余视为草稿。 */
  @Override
  @Transactional
  public AnnotationSaveResultVO saveAnnotation(Long jobId, AnnotationSaveDTO dto, String status) {
    JobVO job = taskService.job(jobId);
    Long assetId = dto.getActiveAssetId() != null ? dto.getActiveAssetId() : firstAssetId(jobId);
    long userId = currentUserId();
    String editMode = dto.getEditMode();
    // 审核员修订模式：独立分支，零状态副作用；写入后立即 return，不走原 saveAnnotation 主体。
    if ("reviewer".equals(editMode)) {
      return saveAnnotationAsReviewer(job, assetId, dto, userId);
    }
    // 多帧批量（点云序列标注）：一次请求写多帧，避免逐帧调用产生大量请求与并发部分失败。
    // frames 为空表示老的单帧请求，走原有逻辑，完全向后兼容。
    if (dto.getFrames() != null && !dto.getFrames().isEmpty()) {
      return saveAnnotationFrames(job, dto, status, userId);
    }
    // 兜底防御：资产处于「审核中/复审中」且当前用户不是该条 reviewer → 一律 403，
    // 防止前端误传 editMode 也无法落库；标注员/管理员照常走原路径（review_status 终态或非审核态时放行）。
    assertNotReviewOnlyAsset(assetId, userId, editMode);
    Long annotationId = persistAssetAnnotation(job, assetId, dto, status, userId, true, null);
    return advanceSubmitStatus(job, assetId, status, userId, annotationId);
  }

  /**
   * 多帧批量落库：点云序列标注一次请求写多个 asset。
   *
   * <p>每帧独立写各自的 annotation header 与对象表，互不串扰；
   * 提交审核的状态推进只在最后以「主帧」执行一次，避免按帧重复流转条目/作业。
   */
  private AnnotationSaveResultVO saveAnnotationFrames(
      JobVO job, AnnotationSaveDTO dto, String status, long userId) {
    Long jobId = job.getId();
    String editMode = dto.getEditMode();
    Long mainAssetId = dto.getActiveAssetId();
    Long mainAnnotationId = null;
    // 各帧对象先汇总，写完所有 header 后一次性落对象表（软删/重插只覆盖本次帧，见 persistAnnotationObjects）
    List<AnnotationObjectDTO> allObjects = new ArrayList<>();
    // 一次取回整作业的 assetId → annotationId，供逐帧写 header 与后面落对象表共用，
    // 避免每帧各查一次（60 帧就是 60 次往返）
    Map<Long, Long> annotationIds = loadAnnotationIds(jobId);
    // 本次 payload 实际覆盖的帧（assetId → annotationId）：软删范围据此收窄 ——
    // 全作业软删会把未发送帧的对象行清掉（增量保存的数据丢失源），必须只碰本次帧
    Map<Long, Long> touchedAnnotationIds = new HashMap<>();
    for (AnnotationFrameDTO frame : dto.getFrames()) {
      if (frame == null || frame.getActiveAssetId() == null) continue;
      Long assetId = frame.getActiveAssetId();
      assertNotReviewOnlyAsset(assetId, userId, editMode);
      // 每帧只序列化本帧内容，避免把整批对象写进每个 asset 的 header
      //（否则同一 objectKey 在 DB 里出现多份，看起来"被删的又回来了"）
      AnnotationSaveDTO one = new AnnotationSaveDTO();
      one.setActiveAssetId(assetId);
      one.setActiveItem(frame.getActiveItem() != null ? frame.getActiveItem() : dto.getActiveItem());
      one.setTool(frame.getTool() != null ? frame.getTool() : dto.getTool());
      one.setObjects(frame.getObjects());
      one.setEditMode(editMode);
      // 帧级全局属性只随「前端显式编辑过」的帧下发；未携带的帧由 persistAssetAnnotation
      // 保留该帧已存的 $.globalAttrs（annotation_json 是整份覆盖，缺省值会被抹掉）
      one.setGlobalAttrs(frame.getGlobalAttrs());
      // 只写 header，对象表稍后统一写
      Long annotationId =
          persistAssetAnnotation(job, assetId, one, status, userId, false, annotationIds.get(assetId));
      if (annotationId != null) {
        annotationIds.put(assetId, annotationId);
        touchedAnnotationIds.put(assetId, annotationId);
      }
      if (frame.getObjects() != null) {
        allObjects.addAll(frame.getObjects());
      }
      if (mainAssetId == null) {
        mainAssetId = assetId;
        mainAnnotationId = annotationId;
      } else if (assetId.equals(mainAssetId)) {
        mainAnnotationId = annotationId;
      }
    }
    if (mainAssetId == null) {
      throw new ServiceException("批量保存未携带任何有效资产，无法落库");
    }
    // 主帧不在 frames 里时补查一次，保证返回值带 annotationId
    if (mainAnnotationId == null) {
      mainAnnotationId = annotationMapper.selectAnnotationIdByJobAsset(jobId, mainAssetId);
    }
    // 各帧 header 写完后，把汇总的对象作为「本次 payload」一次性写入对象表。
    // 软删范围 = touchedAnnotationIds（本次 frames 涉及的帧），未发送帧的对象行原样保留
    persistAnnotationObjects(jobId, mainAssetId, allObjects, touchedAnnotationIds);
    return advanceSubmitStatus(job, mainAssetId, status, userId, mainAnnotationId);
  }

  /**
   * 一次取回整作业的「assetId → annotationId」映射，供批量保存复用。
   *
   * <p>mapper 返回的是行列表（每行含 assetId / annotationId 两列），这里显式取值组装。
   * 不要用 {@code @MapKey} 让 MyBatis 直接返回 Map：那样 value 是整行而不是指定列，
   * 编译期因泛型擦除看不出来，运行时才抛 ClassCastException。
   */
  private Map<Long, Long> loadAnnotationIds(Long jobId) {
    Map<Long, Long> ids = new HashMap<>();
    if (jobId == null) {
      return ids;
    }
    List<LabelAnnotationIdRow> rows = annotationMapper.selectAnnotationIdsByJob(jobId);
    if (rows == null) {
      return ids;
    }
    for (LabelAnnotationIdRow row : rows) {
      if (row.getAssetId() != null && row.getAnnotationId() != null) {
        ids.put(row.getAssetId(), row.getAnnotationId());
      }
    }
    return ids;
  }

  /**
   * 写入单个 asset 的标注存档，返回 annotationId。
   *
   * @param persistObjects 是否同时写对象表。<b>批量保存必须传 false</b>：
   *     {@link #persistAnnotationObjects} 是「全量语义」——它把不在本次 payload 中的 asset
   *     视为残留并软删。若逐帧各自调用，处理第 3 帧时就会把第 10 帧的对象删掉、
   *     处理第 10 帧又把刚写入的第 3 帧删掉，最终只有最后一帧的框存活。
   *     因此批量场景必须先把各帧对象合并，再一次性写入。
   * @param knownAnnotationId 已知的 annotationId（批量保存时由一次查询缓存复用）；
   *                          传 null 时按 jobId + assetId 现查
   */
  private Long persistAssetAnnotation(
      JobVO job, Long assetId, AnnotationSaveDTO dto, String status, long userId,
      boolean persistObjects, Long knownAnnotationId) {
    Long jobId = job.getId();
    Long annotationId =
        knownAnnotationId != null ? knownAnnotationId : annotationMapper.selectAnnotationIdByJobAsset(jobId, assetId);
    // 未携带 globalAttrs 的保存（批量帧 / 对象改动传播）：保留该帧已存的全局属性值 ——
    // annotation_json 是整份覆盖（NON_NULL 下缺省键直接消失），不回填会把旧值抹掉。
    // 只有前端显式带了 globalAttrs（编辑过全局属性面板的帧）才覆盖
    if (dto.getGlobalAttrs() == null && annotationId != null) {
      String saved = annotationMapper.selectAnnotationGlobalAttrs(jobId, assetId);
      if (saved != null && !saved.isBlank() && !"null".equals(saved)) {
        Object savedParsed = parseJson(saved);
        if (savedParsed instanceof Map<?, ?> savedMap) {
          dto.setGlobalAttrs(new LinkedHashMap<>(castMap(savedMap)));
        }
      }
    }
    String annotationJson = toJson(dto);
    String annotationStatus = LabelJobStatus.PENDING_REVIEW.equals(status)
        ? AnnotationStatus.SUBMITTED
        : AnnotationStatus.DRAFT;
    if (annotationId == null) {
      Map<String, Object> p = new HashMap<>();
      p.put("jobId", jobId);
      p.put("assetId", assetId);
      p.put("annotatorId", userId);
      p.put("annotationStatus", annotationStatus);
      p.put("annotationJson", annotationJson);
      p.put("userId", userId);
      annotationMapper.insertAnnotation(p);
      annotationId = asLong(p.get("id"));
    } else {
      Map<String, Object> p = new HashMap<>();
      p.put("annotationId", annotationId);
      p.put("annotationStatus", annotationStatus);
      p.put("annotationJson", annotationJson);
      p.put("userId", userId);
      annotationMapper.updateAnnotation(p);
    }
    // 对象按 assetId 归属写入：dto.getObjects() 只含本帧对象，不能把别帧对象塞进本 asset 的 header
    // → 否则同一 objectKey 在 DB 中出现多份（看起来"被删的又回来了"）。
    // 批量保存时此处跳过，由 saveAnnotationFrames 合并各帧对象后统一调用一次。
    if (persistObjects) {
      // annotationId 刚在上面查出/插入过，直接带入 —— 否则 persistAnnotationObjects
      // 内部会再查一次同一行（单帧路径平白多一次 DB 往返）。
      // 必须是**可变** map：persistAnnotationObjects 会把逐帧解析到的 annotationId 回填进来
      // 图片页 payload 携带全作业对象：软删范围必须覆盖全作业帧，否则「逐帧删除多帧后
      // 统一保存」时，那些被删空的帧既不在 grouped（无对象）、也不在 annotationIds（旧版只有
      // 当前帧），旧对象行不会被软删 → 刷新后被删的框复活。
      Map<Long, Long> scopeIds = loadAnnotationIds(jobId);
      scopeIds.put(assetId, annotationId);
      persistAnnotationObjects(jobId, assetId, dto.getObjects(), scopeIds);
    }
    persistObjectTracks(job, dto.getTracks());
    return annotationId;
  }

  /**
   * 提交审核时的状态推进：以条目（数据集合包）为单位流转，作业状态按条目进度聚合。
   * 草稿保存只更新进度与资产标注态。
   */
  private AnnotationSaveResultVO advanceSubmitStatus(
      JobVO job, Long assetId, String status, long userId, Long annotationId) {
    Long jobId = job.getId();
    if (LabelJobStatus.PENDING_REVIEW.equals(status)) {
      // 提交审核：以条目（数据集合包）为单位流转——包内数据统一置为「已标注/已修订」，
      // 审核状态重置为「未审核」（首次提交→已标注，被驳回后修订提交→已修订）
      if (assetId != null) {
        Long entryId = annotationMapper.selectEntryIdByAssetId(assetId);
        if (entryId != null) {
          String labelStatus =
              inReviseFlow(annotationMapper.selectEntryLabelStatus(entryId))
                  ? LabelEntryStatus.REVISED
                  : LabelEntryStatus.LABELED;
          annotationMapper.submitEntryAssets(entryId, labelStatus, userId);
          annotationMapper.submitEntryStatus(entryId, labelStatus, userId);
          boolean revised = LabelEntryStatus.REVISED.equals(labelStatus);
          entryHistoryService.record(entryId,
              revised ? LabelEntryHistoryAction.SUBMIT_REVISE : LabelEntryHistoryAction.SUBMIT,
              revised ? "条目提交修订" : "条目提交", "标注", null);
        } else {
          // 未归入数据条目的数据资产：仅同步自身状态
          annotationMapper.updateAssetSubmitStatus(assetId, userId);
        }
      }
      // 作业状态同样以条目为单位聚合：作业内条目全部提交后才进入「待审核」，否则仍在标注中
      Map<String, Object> entryProgress = annotationMapper.selectJobEntryProgress(jobId);
      Long totalRaw = entryProgress == null ? null : asLong(entryProgress.get("total"));
      Long doneRaw = entryProgress == null ? null : asLong(entryProgress.get("done"));
      long totalEntries = totalRaw == null ? 0L : totalRaw;
      long doneEntries = doneRaw == null ? 0L : doneRaw;
      // 无条目归属（异常兜底）时按本次提交即待审核处理
      boolean jobSubmitted = totalEntries == 0 || doneEntries >= totalEntries;
      int progress = totalEntries > 0 ? (int) (doneEntries * 100 / totalEntries) : 100;
      annotationMapper.updateJobProgressAndStatus(
          jobId, jobSubmitted ? LabelJobStatus.PENDING_REVIEW : LabelJobStatus.LABELING, progress,
          userId);
      if (jobSubmitted) {
        createNotification(
            findReviewerId(jobId),
            "作业待审核",
            "作业 " + job.getJobNo() + " 已提交审核",
            "job",
            jobId);
      }
      writeOperationLog("SUBMIT_REVIEW", "job", jobId, Map.of("jobNo", job.getJobNo()));
      taskService.syncTaskStatus(job.getTaskId());
    } else {
      int progress = Math.max(job.getProgress() == null ? 0 : job.getProgress(), 70);
      annotationMapper.updateJobProgressAndStatus(jobId, status, progress, userId);
      if (assetId != null) {
        annotationMapper.updateAssetLabelStatus(assetId, LabelEntryStatus.LABELING, userId);
      }
    }
    AnnotationSaveResultVO result = new AnnotationSaveResultVO();
    result.setJobId(jobId);
    result.setAnnotationId(annotationId);
    result.setStatus(status);
    result.setSavedAt(LocalDateTime.now());
    return result;
  }

  /** 资产维度标注查看：定位最近一次标注所在作业后复用作业标注数据。 */
  @Override
  public AssetAnnotationVO assetAnnotation(Long assetId) {
    Long jobId = annotationMapper.selectJobIdByAsset(assetId);
    AssetAnnotationVO result = new AssetAnnotationVO();
    result.setAssetId(assetId);
    if (jobId == null) {
      result.setObjects(List.of());
      return result;
    }
    AnnotationViewVO view = annotation(jobId);
    result.setJobId(jobId);
    result.setJob(view.getJob());
    result.setMediaType(view.getMediaType());
    result.setAssets(view.getAssets());
    result.setActiveAsset(view.getActiveAsset());
    result.setClasses(view.getClasses());
    result.setObjects(view.getObjects());
    return result;
  }

  /** 标注作业列表：标注员视角的作业（待标注/标注中/已退回/已完成）。 */
  @Override
  public PageResult<JobVO> myJobs(String status) {
    // 过滤下推 SQL：按当前用户 + 放行状态集合查询，避免全量作业 JOIN 加载后内存过滤
    List<JobVO> filtered =
        taskService.jobsByFilter(
            currentUserId(),
            List.of(
                LabelJobStatus.PENDING_LABEL,
                LabelJobStatus.LABELING,
                LabelJobStatus.REVIEW_REJECTED,
                LabelJobStatus.COMPLETED),
            status);
    return PageResult.of(filtered);
  }

  // ---------------- 标注对象持久化辅助 ----------------

  /**
   * 按每个对象的 attributes.assetId 路由到对应的 annotation header。
   *
   * <p>前端 payload 是全量语义（整个作业所有 asset 的对象），因此先全量软删该作业的旧行
   * （含历史缺陷产生的重复行与跨 header 拷贝），再插入本次 payload，
   * 保证「保存后 DB 与画布一致」；否则非当前 asset 的对象会随保存次数不断重复累积。
   * 归属不明的对象回退到 activeAssetId。</p>
   *
   * @param annotationIds 整作业的 assetId → annotationId 映射，由批量保存路径一次查出并复用；
   *                      传 null 时按帧逐个补查（单帧路径）
   */
  private void persistAnnotationObjects(
      Long jobId, Long activeAssetId, List<AnnotationObjectDTO> objects, Map<Long, Long> annotationIds) {
    if (objects == null) {
      return;
    }
    long userId = currentUserId();
    Map<Long, List<AnnotationObjectDTO>> grouped = new HashMap<>();
    for (AnnotationObjectDTO object : objects) {
      Long objAssetId = assetIdOf(firstPresent(object.getAttributes(), object.getAttrJson()));
      if (objAssetId == null) {
        objAssetId = activeAssetId;
      }
      grouped.computeIfAbsent(objAssetId, k -> new ArrayList<>()).add(object);
    }
    // 软删范围 = **本次 payload 覆盖的帧** = 有对象的帧（grouped）∪ 调用方声明的帧集合（annotationIds）。
    // 并上 annotationIds 的原因：**被删空的帧**（重做/整帧删除）objects 为空，在 grouped 里缺席，
    // 但它的旧行必须软删 —— 否则「删除后保存成功，重进页面旧框复活」。
    // （不能全作业、也不能想当然只删「当前帧」：
    // - 图片页 payload 携带**全作业**对象（grouped=全部帧）→ 软删全作业，与旧语义一致，不产生重复行；
    // - 点云页增量 payload 只带改动帧 → 只软删改动帧，未发送帧的对象行原样保留（增量安全）；
    // - 若做全作业软删（旧 softDeleteObjectsByJob）：增量保存会清掉未发送帧的对象（数据丢失），
    //   且软删行随保存无限堆积、JOIN 全表扫越来越慢（保存 78 秒的元凶））
    Set<Long> scopeAssetIds = new LinkedHashSet<>(grouped.keySet());
    if (annotationIds != null) {
      scopeAssetIds.addAll(annotationIds.keySet());
    }
    List<Long> scopeAnnotationIds = new ArrayList<>();
    for (Long scopeAssetId : scopeAssetIds) {
      Long scopeAnnId = annotationIds != null ? annotationIds.get(scopeAssetId) : null;
      if (scopeAnnId == null && jobId != null) {
        scopeAnnId = annotationMapper.selectAnnotationIdByJobAsset(jobId, scopeAssetId);
        // 回填：后面分组循环直接命中，不再逐帧重复查询
        if (scopeAnnId != null && annotationIds != null) {
          annotationIds.put(scopeAssetId, scopeAnnId);
        }
      }
      if (scopeAnnId != null) {
        scopeAnnotationIds.add(scopeAnnId);
      }
    }
    if (!scopeAnnotationIds.isEmpty()) {
      annotationMapper.softDeleteObjectsByAnnotationIds(scopeAnnotationIds, userId);
    }
    // 实例批注级联清理：本次 payload 覆盖的帧上，objectKey 已不在提交对象里的实例批注一律软删。
    // 实例被删是系统事实（不校验 created_by，标注员也能清掉审核员写的批注），
    // 否则批注失去宿主一直留着 —— 表现为「帧格标黄、批注列表里找不到对应实例」。
    purgeOrphanObjectComments(jobId, scopeAssetIds, objects, userId);
    // 汇总所有帧的行，最后一次批量写入：每帧各写一次的话 60 帧就是 60 次往返
    List<LabelAnnotationObjectRow> allRows = new ArrayList<>();
    for (Map.Entry<Long, List<AnnotationObjectDTO>> entry : grouped.entrySet()) {
      Long targetAssetId = entry.getKey();
      Long targetAnnotationId = annotationIds == null ? null : annotationIds.get(targetAssetId);
      if (targetAnnotationId == null) {
        // 防御：该帧 header 可能已存在但不在调用方传入的 map 里（历史重复行 / 对象
        // attributes.assetId 指向 payload 未覆盖的帧）。先查再插 —— 直接插的话，
        // 自动保存每 30 秒就给同一帧多插一条 header，加载 JOIN 全部 header 时
        // 老 header 上的对象行全部复活（「删除保存成功、刷新又回来」的制造源）
        targetAnnotationId = annotationMapper.selectAnnotationIdByJobAsset(jobId, targetAssetId);
      }
      if (targetAnnotationId == null) {
        Map<String, Object> p = new HashMap<>();
        p.put("jobId", jobId);
        p.put("assetId", targetAssetId);
        p.put("annotatorId", userId);
        p.put("annotationStatus", "draft");
        p.put("annotationJson", "{}");
        p.put("userId", userId);
        annotationMapper.insertAnnotation(p);
        targetAnnotationId = asLong(p.get("id"));
        if (annotationIds != null && targetAnnotationId != null) {
          annotationIds.put(targetAssetId, targetAnnotationId);
        }
      }
      allRows.addAll(buildObjectRows(targetAnnotationId, jobId, dedupByObjectKey(entry.getValue()), userId));
    }
    insertObjectRows(allRows);
  }

  /**
   * 级联软删「宿主实例已不存在」的实例批注。
   *
   * <p>范围 = 本次保存覆盖的帧（scopeAssetIds）；判定 = 批注 objectKey 不在本次提交的对象集合里。
   * 实例删除是系统事实，这里不校验 created_by —— 标注员删除实例时，审核员写的批注同样要清掉，
   * 否则帧格一直标黄、批注列表里却找不到对应实例。
   *
   * <p>payload 的语义约束与对象落库一致：提交的每帧对象是其全集，因此「不在集合里」等于「已删除」。
   */
  private void purgeOrphanObjectComments(Long jobId, Set<Long> scopeAssetIds,
      List<AnnotationObjectDTO> objects, long userId) {
    if (jobId == null || scopeAssetIds == null || scopeAssetIds.isEmpty()) {
      return;
    }
    Set<String> liveKeys = new LinkedHashSet<>();
    for (AnnotationObjectDTO object : objects) {
      if (object == null || object.getObjectKey() == null) {
        continue;
      }
      String key = object.getObjectKey().trim();
      if (!key.isEmpty()) {
        liveKeys.add(key);
      }
    }
    Map<String, Object> params = new HashMap<>();
    params.put("jobId", jobId);
    params.put("assetIds", new ArrayList<>(scopeAssetIds));
    params.put("objectKeys", new ArrayList<>(liveKeys));
    params.put("userId", userId);
    annotationMapper.purgeOrphanObjectComments(params);
  }

  /**
   * 从对象属性中提取 assetId。属性可能是 Map（反序列化后的对象）或 JSON 文本，
   * 统一成 Map 后取值；无法解析或缺失时返回 null（调用方回退 activeAssetId）。
   */
  private Long assetIdOf(Object rawAttributes) {
    if (rawAttributes == null) {
      return null;
    }
    Object candidate = rawAttributes instanceof Map ? rawAttributes : parseJson(rawAttributes);
    if (!(candidate instanceof Map<?, ?> attrMap)) {
      return null;
    }
    try {
      return asLong(attrMap.get("assetId"));
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  /**
   * 同一 annotation（同一帧 / 同一图）内去重：
   * - objectKey 非空时按 objectKey 去重（沿用历史「后写者赢」语义）；
   * - objectKey 缺失（空白）时，按「几何 + 类别 + 类型」签名兜底去重，
   *   使逐帧复制产生的「同位置、同类别、同类型」重复框合并为唯一一个，
   *   避免同一帧出现两个框、以及由此引发的实例 id 漂移。
   * 几何或类别不同的框仍各自保留（签名不同）。
   */
  private List<AnnotationObjectDTO> dedupByObjectKey(List<AnnotationObjectDTO> objects) {
    LinkedHashMap<String, AnnotationObjectDTO> unique = new LinkedHashMap<>();
    for (AnnotationObjectDTO obj : objects) {
      unique.put(objectIdentity(obj), obj);
    }
    return new ArrayList<>(unique.values());
  }

  /** 计算框在「同一帧」内的唯一标识：objectKey 优先，缺失时退化为几何+类别+类型签名。 */
  private String objectIdentity(AnnotationObjectDTO obj) {
    String key = obj.getObjectKey() == null ? "" : obj.getObjectKey().trim();
    if (!key.isBlank()) {
      return "K:" + key;
    }
    Object geometry = firstPresent(obj.getGeometry(), obj.getGeometryJson());
    String geometryJson = geometry == null ? "" : toJson(geometry);
    return "G:" + obj.getType() + ":" + obj.getClassId() + ":" + geometryJson;
  }

  /**
   * 把一批对象转换成待写入的行，<b>不落库</b>——由 {@link #insertObjectRows} 汇总后统一批量写入。
   *
   * <p>拆成「构造」与「写入」两步，是为了让多帧对象能汇总成一次批量 INSERT：
   * 若每帧各自写，60 帧就是 60 次往返，而对象总行数可能只有一两千、几批就能写完。
   *
   * <p>两个兜底查询（默认对象类型 / 按类名查 classId）原本写在循环里，
   * 一旦前端没传 type 或 classId 就会退化成「每个对象一次 DB 查询」。
   * 这里提到循环外并缓存：对象类型与对象无关，可全局缓存一次；
   * classId 按 className 缓存（不同类别各查一次，通常只有几个类别），不能只缓存第一个值。
   */
  private List<LabelAnnotationObjectRow> buildObjectRows(
      Long annotationId, Long jobId, List<AnnotationObjectDTO> objects, long userId) {
    List<LabelAnnotationObjectRow> rows = new ArrayList<>(objects == null ? 0 : objects.size());
    if (objects == null || objects.isEmpty()) {
      return rows;
    }
    String cachedType = null;
    Map<String, Long> classIdCache = new HashMap<>();
    int index = 1;
    for (AnnotationObjectDTO object : objects) {
      String objectKey = object.getObjectKey() == null ? "" : object.getObjectKey().trim();
      if (objectKey.isBlank()) {
        objectKey = "obj-" + annotationId + "-" + index;
      }
      String objectType = object.getType() == null ? "" : object.getType();
      if (objectType.isBlank()) {
        if (cachedType == null) {
          cachedType = defaultObjectType(jobId);
        }
        objectType = cachedType;
      }
      Long classId = object.getClassId();
      if (classId == null) {
        String className = object.getClassName();
        String cacheKey = className == null ? "" : className;
        classId = classIdCache.computeIfAbsent(cacheKey, k -> findClassId(jobId, className));
      }
      Object geometry = firstPresent(object.getGeometry(), object.getGeometryJson());
      Object attributes = firstPresent(object.getAttributes(), object.getAttrJson());
      // 分组键：前端合组时生成，空白视为未分组（存 NULL 便于 WHERE group_key IS NULL 判定）
      String groupKey = object.getGroupKey() == null ? null : object.getGroupKey().trim();
      if (groupKey != null && groupKey.isEmpty()) {
        groupKey = null;
      }
      // 逐帧（关键帧插值）：落库该帧所属轨迹 Token（仅关键帧入库），空白表示「每帧独立」的传统对象
      // ——历史数据因此无需迁移即可兼容。
      String trackToken = object.getTrackToken();
      if (trackToken != null && trackToken.trim().isEmpty()) {
        trackToken = null;
      }
      LabelAnnotationObjectRow row = new LabelAnnotationObjectRow();
      row.setAnnotationId(annotationId);
      row.setObjectKey(objectKey);
      row.setObjectType(objectType);
      row.setClassId(classId);
      row.setGroupKey(groupKey);
      row.setGeometryJson(toJson(geometry == null ? Map.of() : geometry));
      row.setAttrJson(toJson(attributes == null ? Map.of() : attributes));
      row.setTrackToken(trackToken);
      row.setUserId(userId);
      rows.add(row);
      index++;
    }
    return rows;
  }

  /** 批量落库对象行：按 {@link #OBJECT_BATCH_SIZE} 行一批执行多行 VALUES 的 INSERT。 */
  private void insertObjectRows(List<LabelAnnotationObjectRow> rows) {
    if (rows == null || rows.isEmpty()) {
      return;
    }
    for (int i = 0; i < rows.size(); i += OBJECT_BATCH_SIZE) {
      annotationMapper.insertAnnotationObjectRows(
          rows.subList(i, Math.min(rows.size(), i + OBJECT_BATCH_SIZE)));
    }
  }

  private Long findClassId(Long jobId, String className) {
    if (className != null && !className.isBlank()) {
      Long classId = annotationMapper.selectClassIdByName(jobId, className);
      if (classId != null) {
        return classId;
      }
    }
    return annotationMapper.selectFirstClassId(jobId);
  }

  private String defaultObjectType(Long jobId) {
    JobVO job = taskService.job(jobId);
    return MediaType.POINT_CLOUD.equals(job.getDataType())
        ? AnnotationObjectType.POINT_CLOUD_INSTANCE
        : AnnotationObjectType.POLYGON;
  }

  // ---------------- 对象轨迹持久化（逐帧 / 关键帧插值） ----------------

  /**
   * 落库对象轨迹：只存「存在范围 + 逐帧控制元数据」，几何交给关键帧
   * （随 objects 一起入库，每一行带同一 track_token 的行即一个关键帧）。
   *
   * <p>按 trackToken 增量写入：已存在则更新范围/空洞，否则新建；deleted=true 表示整条轨迹作废。
   * 注意「从下一帧消失」「取消连续」并不删整条轨迹，而是由前端把 lastAssetId 收紧到该帧
   * （前向截断）后在此更新。</p>
   */
  private void persistObjectTracks(JobVO job, List<ObjectTrackDTO> tracks) {
    if (tracks == null || tracks.isEmpty()) {
      return;
    }
    long userId = currentUserId();
    Long jobId = job.getId();
    for (ObjectTrackDTO track : tracks) {
      String token = track.getTrackToken() == null ? null : track.getTrackToken().trim();
      if (token == null || token.isEmpty()) {
        continue;
      }
      if (Boolean.TRUE.equals(track.getDeleted())) {
        annotationMapper.deleteTrack(token, userId);
        continue;
      }
      if (track.getFirstAssetId() == null) {
        continue;
      }
      // 插值方式：当前仅 linear（位置与长宽按帧号均匀变化），为 Catmull-Rom 样条预留扩展位
      String interpolation = track.getInterpolation();
      if (interpolation == null || interpolation.isBlank()) {
        interpolation = "linear";
      }
      List<Long> holes = track.getHoles();
      String holesJson = (holes == null || holes.isEmpty()) ? null : toJson(holes);
      // 虚框区间（实框转虚框、截止到最近关键帧）：[{from,to,dir}]，from/to 为资产ID
      List<Map<String, Object>> gaps = track.getGaps();
      String gapsJson = (gaps == null || gaps.isEmpty()) ? null : toJson(gaps);
      Map<String, Object> p = new HashMap<>();
      p.put("trackToken", token);
      p.put("classId", track.getClassId());
      p.put("firstAssetId", track.getFirstAssetId());
      // lastAssetId 为 null 表示「延续到帧序列末帧」→ 无后续关键帧时保持最后关键帧状态
      p.put("lastAssetId", track.getLastAssetId());
      p.put("holesJson", holesJson);
      p.put("gapsJson", gapsJson);
      p.put("interpolation", interpolation);
      p.put("userId", userId);
      if (annotationMapper.selectTrackId(token) != null) {
        annotationMapper.updateTrack(p);
      } else {
        p.put("jobId", jobId);
        p.put("entryId", track.getEntryId());
        annotationMapper.insertTrack(p);
      }
    }
  }

  // ---------------- 审核员修订模式（editMode=reviewer）----------------

  /**
   * 审核员修订模式保存：仅写标注内容（label_annotation + label_annotation_object），
   * 不修改 label_asset.label_status / review_status / label_data_entry.* / label_job.status，
   * 避免把审核员的领取状态冲掉。
   *
   * <p>鉴权：当前用户必须等于 label_asset.reviewer_id，且
   * review_status ∈ {reviewing, re_reviewing}。审计：通过
   * {@code label_review_record.review_type='reviewer_edit'} 留痕，零 DDL。</p>
   */
  private AnnotationSaveResultVO saveAnnotationAsReviewer(
      JobVO job, Long assetId, AnnotationSaveDTO dto, long userId) {
    if (assetId == null) {
      throw new ServiceException("无法定位当前资产，无法以审核员修订模式保存");
    }
    AssetReviewContextDTO ctx = reviewMapper.selectAssetReviewContext(assetId);
    if (ctx == null) {
      throw new ServiceException("数据资产不存在或已删除");
    }
    String reviewStatus = ctx.getReviewStatus();
    Long reviewerId = ctx.getReviewerId();
    if (!LabelReviewStatus.REVIEWING.equals(reviewStatus)
        && !LabelReviewStatus.RE_REVIEWING.equals(reviewStatus)) {
      throw new ServiceException("当前资产未在审核中，无法以审核员修订模式保存");
    }
    if (reviewerId == null || !reviewerId.equals(userId)) {
      throw new ServiceException("您不是该资产的当前审核员，无修改权限");
    }
    Long jobId = job.getId();
    // 特征日志：确认后端跑的是「处理 frames 的 reviewer 分支」新代码 ——
    // 审核页保存时控制台没有这行 = 后端还在跑旧代码（没重新编译/没重启）
    REVIEWER_LOG.info("[reviewer-save] mainAsset={}, frames={}, mainObjects={}",
        assetId,
        dto.getFrames() == null ? 0 : dto.getFrames().size(),
        dto.getObjects() == null ? 0 : dto.getObjects().size());
    // 帧 → 对象集合：主层对象（当前帧）+ 前端增量载荷的 frames（其余 dirty 帧）。
    // 修复①：之前只落主帧、frames 被整个忽略 —— 审核页在非主帧上的删除/修改全部落空；
    // 修复②：软删范围必须显式带上**所有 touched 帧**（含删空的帧）—— 删空帧在 grouped
    // 里缺席，annotationIds 不传的话它的旧行永远不会被软删，表现为
    // 「审核页删除实例、save-draft 调用成功、刷新页面实例又回来了」
    Map<Long, List<AnnotationObjectDTO>> frameObjects = new HashMap<>();
    frameObjects.put(assetId, dto.getObjects() == null ? new ArrayList<>() : dto.getObjects());
    if (dto.getFrames() != null) {
      for (AnnotationFrameDTO frame : dto.getFrames()) {
        if (frame == null || frame.getActiveAssetId() == null) continue;
        frameObjects.put(frame.getActiveAssetId(),
            frame.getObjects() == null ? new ArrayList<>() : frame.getObjects());
      }
    }
    // 一次批量取回整作业 assetId → annotationId（与多帧分支 loadAnnotationIds 同口径），
    // 主帧与各帧 header 都从这里查，避免逐帧 select（60 帧就是 60 次往返）。
    // 注意：软删范围不能用这个全量 map —— 它含未发送帧，会造成未发送帧对象被误删，
    // 必须单独维护「本次实际覆盖的帧」集合（touchedAnnotationIds）
    Map<Long, Long> allAnnotationIds = loadAnnotationIds(jobId);
    Map<Long, Long> touchedAnnotationIds = new HashMap<>();
    for (Map.Entry<Long, List<AnnotationObjectDTO>> entry : frameObjects.entrySet()) {
      Long frameAssetId = entry.getKey();
      if (frameAssetId.equals(assetId)) continue; // 主帧 header 已在下方单独写
      AnnotationSaveDTO one = new AnnotationSaveDTO();
      one.setActiveAssetId(frameAssetId);
      one.setActiveItem(dto.getActiveItem());
      one.setTool(dto.getTool());
      one.setObjects(entry.getValue());
      one.setEditMode("reviewer");
      // 只写各帧 header（零状态副作用：不碰 asset/job 状态），对象表稍后统一落。
      // status 传 PENDING_REVIEW：persistAssetAnnotation 内部把它映射为 header 的
      // SUBMITTED，与下方主帧 header 写入口径一致；
      // 已有 header 的帧直接带 knownAnnotationId，persistAssetAnnotation 内部不再查库
      Long frameAnnId = persistAssetAnnotation(
          job, frameAssetId, one, LabelJobStatus.PENDING_REVIEW, userId, false,
          allAnnotationIds.get(frameAssetId));
      touchedAnnotationIds.put(frameAssetId, frameAnnId);
    }
    String annotationJson = toJson(dto);
    Long annotationId = allAnnotationIds.get(assetId);
    if (annotationId == null) {
      Map<String, Object> p = new HashMap<>();
      p.put("jobId", jobId);
      p.put("assetId", assetId);
      p.put("annotatorId", userId);
      p.put("annotationStatus", AnnotationStatus.SUBMITTED);
      p.put("annotationJson", annotationJson);
      p.put("userId", userId);
      annotationMapper.insertAnnotation(p);
      annotationId = asLong(p.get("id"));
    } else {
      Map<String, Object> p = new HashMap<>();
      p.put("annotationId", annotationId);
      p.put("annotationStatus", AnnotationStatus.SUBMITTED);
      p.put("annotationJson", annotationJson);
      p.put("userId", userId);
      annotationMapper.updateAnnotation(p);
    }
    // 主帧也要进 touched：persistAnnotationObjects 对 touched 里缺席的帧会走
    // 「无 header 就新插」分支 —— 主帧 header 明明刚写过，再插就撞唯一键（保存报错），
    // 即便不撞约束也会多出一条 draft 状态的重复 header 行
    touchedAnnotationIds.put(assetId, annotationId);
    // 所有 touched 帧（含删空帧）统一软删旧行 + 重插本次对象；帧归属按 attributes.assetId 归组
    List<AnnotationObjectDTO> allObjects = new ArrayList<>();
    for (List<AnnotationObjectDTO> list : frameObjects.values()) {
      allObjects.addAll(list);
    }
    persistAnnotationObjects(jobId, assetId, allObjects, touchedAnnotationIds);
    // 审计留痕：复用 ReviewMapper.insertReviewRecord，review_type='reviewer_edit'，零 DDL。
    Map<String, Object> rec = new HashMap<>();
    rec.put("jobId", jobId);
    rec.put("assetId", assetId);
    rec.put("entryId", ctx.getEntryId());
    rec.put("annotationId", annotationId);
    rec.put("reviewerId", userId);
    rec.put("reviewType", LabelReviewRecord.TYPE_REVIEWER_EDIT);
    rec.put("result", LabelReviewRecord.RESULT_PASSED);
    rec.put("comment", "审核员修订标注");
    rec.put("userId", userId);
    reviewMapper.insertReviewRecord(rec);
    AnnotationSaveResultVO result = new AnnotationSaveResultVO();
    result.setJobId(jobId);
    result.setAnnotationId(annotationId);
    // 语义：审核员修订模式下，标注始终处于「已提交、待审核」视角，不推进 job 状态。
    result.setStatus(LabelJobStatus.PENDING_REVIEW);
    result.setSavedAt(LocalDateTime.now());
    return result;
  }

  /**
   * 兜底防御：若当前资产处于「审核中/复审中」且当前用户不是该条 reviewer，一律拒绝写入
   * （即便前端忘记传 editMode=reviewer）。仅在 editMode 不为 reviewer 时调用，避免双校验。
   */
  private void assertNotReviewOnlyAsset(Long assetId, long userId, String editMode) {
    if (assetId == null || "reviewer".equals(editMode)) {
      return;
    }
    AssetReviewContextDTO ctx = reviewMapper.selectAssetReviewContext(assetId);
    if (ctx == null) {
      return;
    }
    String reviewStatus = ctx.getReviewStatus();
    Long reviewerId = ctx.getReviewerId();
    boolean inReviewing = LabelReviewStatus.REVIEWING.equals(reviewStatus)
        || LabelReviewStatus.RE_REVIEWING.equals(reviewStatus);
    if (inReviewing && (reviewerId == null || !reviewerId.equals(userId))) {
      throw new ServiceException("该资产正在审核中，仅当前审核员可修改");
    }
  }

  @Override
  public List<ObjectCommentVO> objectComments(Long jobId, Long assetId) {
    if (jobId == null) {
      return List.of();
    }
    return annotationMapper.selectObjectComments(jobId, assetId);
  }

  @Override
  public Long addObjectComment(Long jobId, ObjectCommentDTO dto) {
    if (jobId == null || dto == null) {
      throw new ServiceException("批注内容不能为空");
    }
    // 审核节点批注＝类型（错误内容）+ 描述，两者至少填一项；标注环节历史只传描述
    String commentType = trimToNull(dto.getCommentType());
    String content = dto.getContent() == null ? "" : dto.getContent().trim();
    if (content.isEmpty() && commentType == null) {
      throw new ServiceException("批注内容不能为空");
    }
    if (content.length() > MAX_COMMENT_CONTENT_LENGTH) {
      throw new ServiceException("批注描述不能超过 " + MAX_COMMENT_CONTENT_LENGTH + " 字");
    }
    if (commentType != null && commentType.length() > MAX_COMMENT_TYPE_LENGTH) {
      throw new ServiceException("批注类型不能超过 " + MAX_COMMENT_TYPE_LENGTH + " 字");
    }
    Long userId = currentUserId();
    LabelObjectComment comment = new LabelObjectComment();
    comment.setJobId(jobId);
    comment.setAssetId(dto.getAssetId());
    comment.setObjectKey(trimToNull(dto.getObjectKey()));
    comment.setNode(dto.getNode() == null || dto.getNode().isBlank() ? "label" : dto.getNode().trim());
    comment.setCommentType(commentType);
    // content 列为 NOT NULL，未填描述时落空串（前端可只选类型）
    comment.setContent(content);
    comment.setPosX(dto.getPosX());
    comment.setPosY(dto.getPosY());
    comment.setPosZ(dto.getPosZ());
    comment.setCreatedBy(userId);
    comment.setUpdatedBy(userId);
    annotationMapper.insertObjectComment(comment);
    return comment.getId();
  }

  /** 去首尾空白，空串返回 null（批注类型/对象 key 统一按 null 表示未填）。 */
  private String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String text = value.trim();
    return text.isEmpty() ? null : text;
  }

  @Override
  public void deleteObjectComment(Long commentId) {
    if (commentId == null) {
      return;
    }
    annotationMapper.deleteObjectComment(commentId, currentUserId());
  }

  @Override
  public void updateObjectCommentStatus(Long commentId, String status) {
    if (commentId == null) {
      return;
    }
    // 只允许在两个合法状态间切换，避免前端传任意值污染数据
    String next = status == null ? "" : status.trim();
    if (!"open".equals(next) && !"corrected".equals(next)) {
      throw new ServiceException("批注状态不合法");
    }
    if (annotationMapper.updateObjectCommentStatus(commentId, next, currentUserId()) == 0) {
      throw new ServiceException("批注不存在或已删除");
    }
  }

  @Override
  public void markFrameInvalid(Long assetId, boolean invalid, String reason) {
    if (assetId == null) {
      throw new ServiceException("资产ID不能为空");
    }
    Long userId = currentUserId();
    String flag = invalid ? "invalid" : "normal";
    // 原因非必填：标注/审核页只打有效性标记；传了原因就记下来，没传就留空
    String invalidReason = invalid && reason != null && !reason.isBlank() ? reason.trim() : null;
    annotationMapper.updateAssetFrameFlag(assetId, flag, invalidReason, userId);
  }

  /** 条目是否处于修订链路：被驳回后重新提交应记为「已修订」，否则记为「已标注」。 */
  private boolean inReviseFlow(String entryLabelStatus) {
    return LabelEntryStatus.UNREVISED.equals(entryLabelStatus)
        || LabelEntryStatus.REVISING.equals(entryLabelStatus)
        || LabelEntryStatus.REVISED.equals(entryLabelStatus);
  }
}
