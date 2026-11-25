package org.dromara.label.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.label.common.NuscenesMetaSupport;
import org.dromara.label.common.NuscenesMetaSupport.NuscenesIndex;
import org.dromara.label.dto.NuscenesMetaBundle;
import org.dromara.label.mapper.PreAnnotationMapper;
import org.dromara.label.mapper.ProjectMapper;
import org.dromara.label.parser.PreAnnotationParseResult;
import org.dromara.label.parser.PreAnnotationParser;
import org.dromara.label.service.BaseService;
import org.dromara.label.service.PreAnnotationService;
import org.dromara.label.vo.PreAnnotationAssetVO;
import org.dromara.label.vo.PreAnnotationObjectVO;
import org.dromara.label.vo.PreAnnotationResultVO;
import org.dromara.label.vo.PreAnnotationVO;
import org.dromara.label.vo.WorkClassVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 预标注域服务实现。
 *
 * <p>解析 nuScenes 数据集元数据（v1.0-trainval/*.json），把官方 3D 框
 * （sample_annotation）从 global 坐标系转换到雷达传感器坐标系（global -&gt; ego -&gt; sensor），
 * 按「文件名」匹配数据资产（label_asset.file_name）后落库。</p>
 *
 * <p>两个入口共用同一套转换逻辑：
 * 1. 手动入口：上传 nuScenes 压缩包（批次面板 / 资产页的「预标注」弹窗）；
 * 2. 自动入口：nuScenes 数据集导入链路解析归档元数据后触发（见 NuscenesImportServiceImpl）。</p>
 */
@Service
public class PreAnnotationServiceImpl extends BaseService implements PreAnnotationService {

  /** 预标注来源标识 */
  private static final String SOURCE_NUSCENES = "nuscenes";

  /** 预标注状态：已生成 */
  private static final String STATUS_READY = "ready";

  /** 预标注框对象类型 */
  private static final String OBJECT_TYPE_CUBOID = "cuboid";

  /** 几何 kind 字段值：立体框 */
  private static final String KIND_CUBOID = "cuboid";

  /** zip 条目读取缓冲区大小（字节） */
  private static final int ENTRY_BUFFER_SIZE = 8192;

  /** 各字段截断上限（与表结构列宽一致） */
  private static final int LIMIT_SOURCE_FILE = 255;
  private static final int LIMIT_OBJECT_KEY = 80;
  private static final int LIMIT_CLASS_NAME = 100;
  private static final int LIMIT_TRACK_TOKEN = 80;
  private static final int LIMIT_VISIBILITY = 20;
  private static final int LIMIT_SAMPLE_TOKEN = 80;
  private static final int LIMIT_SENSOR_CHANNEL = 50;

  /** 对象 Key 兜底长度（UUID 去横线） */
  private static final String UUID_SEPARATOR = "-";

  /**
   * 自动补建标签时的分配色板。源格式自带颜色的（如 YOLO 的 class.json）优先用源颜色；
   * COCO / 点位导出没有颜色信息，就从这里按顺序取，避免新标签全是一个色。
   */
  private static final String[] CLASS_PALETTE = {
      "#4f8cff", "#67c23a", "#e6a23c", "#f56c6c", "#909399",
      "#b56bff", "#5cd6c0", "#e0559f", "#8a7bff", "#3fa8e0"
  };

  /** 自动补建标签的默认标注工具：拿不到几何类型时，图片场景绝大多数是矩形框 */
  private static final String DEFAULT_TOOL_TYPE = "bbox";

  @Autowired
  private PreAnnotationMapper preAnnotationMapper;

  /**
   * 全部格式解析器（Spring 注入所有 PreAnnotationParser 实现）。
   * 新增一种格式只需增加实现类，主流程无需改动。
   */
  @Autowired
  private List<PreAnnotationParser> parsers;

  /** 复用项目模块已有的建标签 / 建标签集 SQL，避免重复写 INSERT */
  @Autowired
  private ProjectMapper projectMapper;

  @Override
  @Transactional(rollbackFor = Exception.class)
  public PreAnnotationResultVO preAnnotateUpload(Long uploadId, MultipartFile file, String format) {
    if (uploadId == null) {
      throw new ServiceException("上传数据ID不能为空");
    }
    List<PreAnnotationAssetVO> assets = preAnnotationMapper.selectAssetsByUpload(uploadId);
    if (assets == null || assets.isEmpty()) {
      throw new ServiceException("该上传数据下暂无数据资产，无法预标注");
    }
    return doPreAnnotate(assets, file, format);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public PreAnnotationResultVO preAnnotateAsset(Long assetId, MultipartFile file, String format) {
    if (assetId == null) {
      throw new ServiceException("数据资产ID不能为空");
    }
    PreAnnotationAssetVO asset = preAnnotationMapper.selectAsset(assetId);
    if (asset == null) {
      throw new ServiceException("数据资产不存在或已删除");
    }
    return doPreAnnotate(List.of(asset), file, format);
  }

  @Override
  public PreAnnotationVO preAnnotation(Long assetId) {
    if (assetId == null) {
      throw new ServiceException("数据资产ID不能为空");
    }
    PreAnnotationVO vo = preAnnotationMapper.selectPreAnnotation(assetId);
    if (vo == null) {
      return null;
    }
    PreAnnotationAssetVO asset = preAnnotationMapper.selectAsset(assetId);
    if (asset != null) {
      vo.setFileName(asset.getFileName());
    }
    List<PreAnnotationObjectVO> objects = preAnnotationMapper.selectPreAnnotationObjects(assetId);
    vo.setObjects(objects == null ? List.of() : objects);
    return vo;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public PreAnnotationResultVO preAnnotateAssets(List<PreAnnotationAssetVO> assets, NuscenesMetaBundle bundle) {
    if (assets == null || assets.isEmpty()) {
      throw new ServiceException("待预标注的数据资产为空，无法预标注");
    }
    if (bundle == null || bundle.getTables() == null || bundle.getTables().isEmpty()) {
      throw new ServiceException("nuScenes 元数据为空，无法预标注");
    }
    return preAnnotateWithRaw(assets, bundle.getTables(), bundle.getSourceFile());
  }

  // ---------------- 主流程 ----------------

  /**
   * 手动入口：按 format 选择解析器，解析上传文件后执行预标注。
   *
   * @param format 格式标识（nuscenes / coco / yolo / point），为空按 nuScenes 处理
   */
  private PreAnnotationResultVO doPreAnnotate(List<PreAnnotationAssetVO> assets, MultipartFile file,
                                              String format) {
    if (file == null || file.isEmpty()) {
      throw new ServiceException("请选择标注文件");
    }
    PreAnnotationParser parser = parserOf(format);
    if (parser != null) {
      // 图片类格式：解析器产出「文件匹配键 → 框列表」+ 源类别定义，
      // 由 preAnnotateByFileKey 统一完成「匹配资产 → 匹配/补建标签 → 落库」
      PreAnnotationParseResult parsed = parser.parse(file);
      return preAnnotateByFileKey(assets, parsed, parser.source(), file.getOriginalFilename());
    }
    Map<String, List<Map<String, Object>>> raw = readNuscenesJson(file);
    return preAnnotateWithRaw(assets, raw, file.getOriginalFilename());
  }

  /** 按格式标识取解析器；nuScenes 或空值返回 null —— 表示走原有的解压缩包路径。 */
  private PreAnnotationParser parserOf(String format) {
    String key = format == null ? "" : format.trim().toLowerCase(Locale.ROOT);
    if (key.isEmpty() || SOURCE_NUSCENES.equals(key)) {
      return null;
    }
    List<String> supported = new ArrayList<>();
    supported.add(SOURCE_NUSCENES);
    if (parsers != null) {
      for (PreAnnotationParser parser : parsers) {
        supported.add(parser.source());
        if (parser.source().equals(key)) {
          return parser;
        }
      }
    }
    throw new ServiceException("不支持的预标注格式：" + format + "，当前支持 " + String.join(" / ", supported));
  }

  /**
   * 图片类格式（COCO / YOLO / 点位导出）预标注主流程。
   *
   * <p>解析器已把文件解析成「文件匹配键 → 框列表」，这里负责剩下的通用步骤：
   * 按文件名匹配资产 → 还原 YOLO 归一化坐标 → 匹配项目标签 → 落库。
   * 落库与 nuScenes 分支共用 {@link #savePreAnnotation}。</p>
   */
  private PreAnnotationResultVO preAnnotateByFileKey(List<PreAnnotationAssetVO> assets,
                                                     PreAnnotationParseResult parsed,
                                                     String source, String sourceFile) {
    Map<String, List<Map<String, Object>>> boxesByFileKey = parsed.getBoxesByFileKey();
    if (boxesByFileKey == null || boxesByFileKey.isEmpty()) {
      throw new ServiceException("未从文件中解析出任何标注框");
    }
    Long userId = currentUserId();
    // 源类别定义：类别名 → 颜色。项目里没有的标签按它补建，能带上原颜色最好
    Map<String, String> sourceColors = new LinkedHashMap<>();
    for (Map<String, Object> definition : parsed.getClassDefinitions()) {
      Object name = definition.get("name");
      if (name == null) {
        continue;
      }
      Object color = definition.get("color");
      sourceColors.put(String.valueOf(name).trim(), color == null ? null : String.valueOf(color).trim());
    }
    // 本次自动补建的标签名，结果里回报——项目被动了什么必须让操作者看见
    List<String> createdClassNames = new ArrayList<>();
    PreAnnotationResultVO result = new PreAnnotationResultVO();
    result.setSourceFile(sourceFile);
    result.setAssetTotal(assets.size());
    // 同一项目的标签集只需查一次
    Map<Long, List<WorkClassVO>> classCache = new HashMap<>();
    int matchedAsset = 0;
    int boxTotal = 0;
    int matchedClass = 0;
    int unmatchedClass = 0;
    int skippedNoSize = 0;
    Long uploadId = null;

    for (PreAnnotationAssetVO asset : assets) {
      if (asset == null || asset.getAssetId() == null) {
        continue;
      }
      if (uploadId == null) {
        uploadId = asset.getUploadId();
      }
      List<Map<String, Object>> boxes = boxesOfAsset(asset, boxesByFileKey);
      if (boxes == null || boxes.isEmpty()) {
        continue;
      }
      // YOLO 是 0~1 归一化坐标，必须乘以原图宽高才能定位；资产宽高缺失则无法还原，只能跳过
      boolean normalized = Boolean.TRUE.equals(boxes.get(0).get("normalized"));
      Integer width = asset.getWidth();
      Integer height = asset.getHeight();
      if (normalized && (width == null || width <= 0 || height == null || height <= 0)) {
        skippedNoSize++;
        continue;
      }
      List<Map<String, Object>> prepared = new ArrayList<>();
      for (Map<String, Object> box : boxes) {
        Map<String, Object> one = new HashMap<>(box);
        Object geometry = box.get("geometry");
        if (normalized && geometry instanceof Map<?, ?>) {
          geometry = denormalize(castMap(geometry), width, height);
        }
        one.put("geometryJson", toJson(geometry));
        one.put("attrJson", toJson(box.get("attr")));
        // 先在项目现有标签里精确匹配（图片格式禁用模糊匹配：类别名普遍短且相似，
        // 静默错配比漏匹配更难发现）；匹配不到则按源类别定义补建一个新标签
        Object sourceClassName = box.get("sourceClassName");
        one.put("classId", sourceClassName == null ? null
            : ensureClassId(asset.getProjectId(), String.valueOf(sourceClassName),
                            toolTypeOf(box.get("objectType")),
                            sourceColors, classCache, createdClassNames));
        prepared.add(one);
      }
      matchedAsset++;
      savePreAnnotation(asset, prepared, sourceFile, userId, source);
      boxTotal += prepared.size();
      for (Map<String, Object> box : prepared) {
        if (box.get("classId") == null) {
          unmatchedClass++;
        } else {
          matchedClass++;
        }
      }
    }
    if (matchedAsset == 0 && skippedNoSize > 0) {
      throw new ServiceException("没有资产可写入：这些资产缺少图片宽高，无法还原 YOLO 归一化坐标");
    }
    if (matchedAsset == 0) {
      // 把两侧算出来的键直接摊开给操作者看：名字「看着一样」但对不上，
      // 往往就差在扩展名、大小写、多一段目录这类肉眼容易忽略的地方
      throw new ServiceException("未能按文件名匹配到任何数据资产。"
          + "标注文件中的名称：" + sampleNames(boxesByFileKey.keySet(), 5)
          + "；当前数据资产的名称：" + sampleAssetNames(assets, 5)
          + "。匹配只比较文件名（忽略扩展名、不区分大小写）。");
    }
    result.setUploadId(uploadId);
    result.setMatchedAsset(matchedAsset);
    result.setBoxTotal(boxTotal);
    result.setMatchedClassObject(matchedClass);
    result.setUnmatchedClassObject(unmatchedClass);
    // 源格式没带类别定义时提示前端：框已导入但没有类别名，需要人工指定标签
    result.setClassDefinitionMissing(parsed.getClassDefinitions().isEmpty());
    result.setCreatedClassCount(createdClassNames.size());
    result.setCreatedClassNames(createdClassNames);
    return result;
  }

  /**
   * 按框的几何类型推断标签的标注工具。
   *
   * <p>{@code insertClass} 的 tool_type 走 {@code NULLIF(#{toolType}, '')} 可空写入，
   * 漏传会落成 NULL——标注页拿不到工具类型，就不知道这个标签该用什么画，
   * 表现为「标签补上了、但标注工具没设定」。</p>
   */
  private String toolTypeOf(Object objectType) {
    String type = objectType == null
        ? "" : String.valueOf(objectType).trim().toLowerCase(Locale.ROOT);
    return switch (type) {
      case "polygon", "segmentation" -> "polygon";
      case "keypoint", "skeleton" -> "keypoint";
      case "brush" -> "brush";
      case "polyline" -> "polyline";
      default -> DEFAULT_TOOL_TYPE;
    };
  }

  /**
   * 取出真正命中的那个标签 VO（供读取工具类型等附加字段）。
   * 复用 {@link #matchClassId} 的匹配口径，避免两处逻辑走偏。
   */
  private WorkClassVO matchedClass(List<WorkClassVO> classes, String sourceName) {
    Long classId = matchClassId(classes, sourceName, false);
    if (classId == null) {
      return null;
    }
    for (WorkClassVO item : classes) {
      if (classId.equals(item.getClassId())) {
        return item;
      }
    }
    return null;
  }

  /**
   * 确定框的项目标签：先在项目现有标签里精确匹配，匹配不到就<b>按源类别定义补建</b>一个。
   *
   * <p>之所以自动补建：YOLO 的 class.json、COCO 的 categories 本身就是权威的标签设置，
   * 让标注员对着一堆「未匹配」手工重建既费时又容易写错名字。</p>
   *
   * @param classCache 项目标签缓存。<b>补建后必须刷新</b>，
   *                   否则同项目后续资产仍匹配不到新标签，会对同一个名字重复建
   */
  private Long ensureClassId(Long projectId, String sourceName, String toolType,
                             Map<String, String> sourceColors,
                             Map<Long, List<WorkClassVO>> classCache, List<String> createdClassNames) {
    if (projectId == null || sourceName == null || sourceName.isBlank()) {
      return null;
    }
    String name = sourceName.trim();
    List<WorkClassVO> classes = classCache.computeIfAbsent(projectId,
        id -> preAnnotationMapper.selectProjectClasses(id));
    String resolvedTool = toolType == null || toolType.isBlank() ? DEFAULT_TOOL_TYPE : toolType.trim();
    Long classId = matchClassId(classes, name, false);
    if (classId != null) {
      // 命中已有标签：若它缺工具类型，顺手按本次框的几何类型补齐——
      // 否则标注页不知道该用什么工具画这次导入进来的框。只补空值，已有的不动
      WorkClassVO existing = matchedClass(classes, name);
      if (existing != null
          && (existing.getToolType() == null || existing.getToolType().isBlank())) {
        preAnnotationMapper.updateClassToolType(classId, resolvedTool, currentUserId());
        classCache.put(projectId, preAnnotationMapper.selectProjectClasses(projectId));
      }
      return classId;
    }
    String color = sourceColors.get(name);
    if (color == null || color.isBlank()) {
      // 源格式没给颜色（COCO / 点位导出没有），按色板顺序分配
      color = CLASS_PALETTE[createdClassNames.size() % CLASS_PALETTE.length];
    }
    Long created = insertClassForProject(projectId, name, color, toolType);
    if (created != null && !createdClassNames.contains(name)) {
      createdClassNames.add(name);
    }
    // 关键：刷新缓存，否则同一项目的下一个资产还会为同名标签再建一次
    classCache.put(projectId, preAnnotationMapper.selectProjectClasses(projectId));
    return created;
  }

  /**
   * 为项目补建一个标签。
   *
   * <p>注意 {@code label_class} 不直接挂项目，而是挂在标签集下
   * （{@code label_class.class_set_id → label_class_set.project_id}），
   * 所以要先定位标签集；未启动的项目可能还没有，这里兜底建一个。</p>
   */
  private Long insertClassForProject(Long projectId, String className, String color, String toolType) {
    Long classSetId = preAnnotationMapper.selectClassSetId(projectId);
    if (classSetId == null) {
      classSetId = createClassSetForProject(projectId);
    }
    if (classSetId == null) {
      return null;
    }
    Integer maxSort = preAnnotationMapper.selectMaxClassSortOrder(classSetId);
    Map<String, Object> params = new HashMap<>();
    params.put("classSetId", classSetId);
    params.put("labelName", className);
    params.put("color", color);
    // 标注工具：漏传会落成 NULL，标注页不知道这个标签该用什么工具画
    params.put("toolType", toolType == null || toolType.isBlank()
        ? DEFAULT_TOOL_TYPE : toolType.trim());
    // 现有建标签流程不自动算序号，这里排到末尾
    params.put("sortOrder", (maxSort == null ? 0 : maxSort) + 1);
    params.put("userId", currentUserId());
    projectMapper.insertClass(params);
    return asLong(params.get("id"));
  }

  /** 项目还没有标签集时建一个（未启动 / 待配置的项目允许没有标签集）。 */
  private Long createClassSetForProject(Long projectId) {
    Map<String, Object> params = new HashMap<>();
    params.put("projectId", projectId);
    params.put("name", "默认标签集");
    params.put("version", "v1");
    params.put("userId", currentUserId());
    projectMapper.insertClassSet(params);
    return asLong(params.get("id"));
  }

  /** 取前若干个名字，用于把匹配失败的实情摊开给操作者看（不甩出几百个） */
  private String sampleNames(Collection<String> names, int limit) {
    StringBuilder text = new StringBuilder("[");
    int count = 0;
    for (String name : names) {
      if (count >= limit) {
        text.append("…");
        break;
      }
      if (count > 0) {
        text.append("、");
      }
      text.append(name);
      count++;
    }
    return text.append("]").toString();
  }

  /** 资产侧的样本键，口径与匹配时完全一致，方便直接肉眼对比差在哪 */
  private String sampleAssetNames(List<PreAnnotationAssetVO> assets, int limit) {
    List<String> keys = new ArrayList<>();
    for (PreAnnotationAssetVO asset : assets) {
      if (asset == null || keys.size() >= limit) {
        continue;
      }
      String key = NuscenesMetaSupport.dataFileKey(asset.getFileName());
      if (key.isEmpty()) {
        key = NuscenesMetaSupport.dataFileKey(asset.getFileUrl());
      }
      if (!key.isEmpty()) {
        keys.add(key);
      }
    }
    return sampleNames(keys, limit);
  }

  /** 按文件名匹配资产的标注：文件名键优先，file_url 键兜底（与 nuScenes 一致的双保险）。 */
  private List<Map<String, Object>> boxesOfAsset(PreAnnotationAssetVO asset,
                                                 Map<String, List<Map<String, Object>>> boxesByFileKey) {
    String fileKey = NuscenesMetaSupport.dataFileKey(asset.getFileName());
    if (!fileKey.isEmpty()) {
      List<Map<String, Object>> boxes = boxesByFileKey.get(fileKey);
      if (boxes != null && !boxes.isEmpty()) {
        return boxes;
      }
    }
    String url = asset.getFileUrl();
    if (url != null && !url.isBlank()) {
      return boxesByFileKey.get(NuscenesMetaSupport.dataFileKey(url));
    }
    return null;
  }

  /**
   * 归一化 geometry → 绝对像素 geometry（YOLO 专用）。
   * bbox / keypoint 缩放 x/y/width/height，polygon / skeleton 缩放各顶点。
   */
  private Map<String, Object> denormalize(Map<String, Object> geometry, int width, int height) {
    Map<String, Object> result = new LinkedHashMap<>(geometry);
    result.remove("normalized");
    Object points = result.get("points");
    if (points instanceof List<?> list) {
      List<Map<String, Object>> scaled = new ArrayList<>();
      for (Object item : list) {
        Map<String, Object> point = new LinkedHashMap<>(castMap(item));
        point.put("x", NuscenesMetaSupport.round4(num(point.get("x")) * width));
        point.put("y", NuscenesMetaSupport.round4(num(point.get("y")) * height));
        scaled.add(point);
      }
      result.put("points", scaled);
      return result;
    }
    if (result.containsKey("x")) {
      result.put("x", NuscenesMetaSupport.round4(num(result.get("x")) * width));
    }
    if (result.containsKey("y")) {
      result.put("y", NuscenesMetaSupport.round4(num(result.get("y")) * height));
    }
    if (result.containsKey("width")) {
      result.put("width", NuscenesMetaSupport.round4(num(result.get("width")) * width));
    }
    if (result.containsKey("height")) {
      result.put("height", NuscenesMetaSupport.round4(num(result.get("height")) * height));
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> castMap(Object value) {
    return value instanceof Map<?, ?> map ? (Map<String, Object>) map : new HashMap<>();
  }

  private double num(Object value) {
    return value instanceof Number number ? number.doubleValue() : 0D;
  }

  /**
   * 预标注公共主流程：校验必需元数据 -&gt; 构建索引 -&gt; 逐资产匹配并转换 3D 框 -&gt; 覆盖落库。
   */
  private PreAnnotationResultVO preAnnotateWithRaw(List<PreAnnotationAssetVO> assets,
                                                   Map<String, List<Map<String, Object>>> raw,
                                                   String sourceFile) {
    for (String required : NuscenesMetaSupport.REQUIRED_JSON) {
      if (!raw.containsKey(required)) {
        throw new ServiceException("压缩包中缺少 nuScenes 元数据 " + required
            + ".json，请确认上传的是 nuScenes 数据集压缩包");
      }
    }

    NuscenesIndex index = NuscenesMetaSupport.buildIndex(raw);
    if (index.sampleDataByFileKey.isEmpty()) {
      throw new ServiceException("未从压缩包中解析到点云样本数据，无法生成预标注");
    }

    Long userId = currentUserId();

    PreAnnotationResultVO result = new PreAnnotationResultVO();
    result.setSourceFile(sourceFile);
    result.setAssetTotal(assets.size());
    int matchedAsset = 0;
    int boxTotal = 0;
    int matchedClass = 0;
    int unmatchedClass = 0;
    Long uploadId = null;
    // 实例维度漏标统计：同一实例在多帧出现，缺帧数只按实例计一次，避免逐框累加虚高
    Map<String, Integer> instanceMissing = new LinkedHashMap<>();

    for (PreAnnotationAssetVO asset : assets) {
      if (asset == null || asset.getAssetId() == null) {
        continue;
      }
      if (uploadId == null) {
        uploadId = asset.getUploadId();
      }
      List<Map<String, Object>> boxes = buildBoxes(index, asset);
      if (boxes.isEmpty()) {
        continue;
      }
      matchedAsset++;
      savePreAnnotation(asset, boxes, sourceFile, userId, SOURCE_NUSCENES);
      boxTotal += boxes.size();
      for (Map<String, Object> box : boxes) {
        if (box.get("classId") == null) {
          unmatchedClass++;
        } else {
          matchedClass++;
        }
        collectInstanceMissing(instanceMissing, box);
      }
    }

    result.setUploadId(uploadId);
    result.setMatchedAsset(matchedAsset);
    result.setBoxTotal(boxTotal);
    result.setMatchedClassObject(matchedClass);
    result.setUnmatchedClassObject(unmatchedClass);
    result.setInstanceCount(instanceMissing.size());
    int gapInstance = 0;
    int missingTotal = 0;
    for (Integer missing : instanceMissing.values()) {
      if (missing != null && missing > 0) {
        gapInstance++;
        missingTotal += missing;
      }
    }
    result.setGapInstanceCount(gapInstance);
    result.setMissingFrameTotal(missingTotal);
    return result;
  }

  /**
   * 覆盖式落库：先软删该资产的历史预标注，再写入本次结果。
   *
   * @param source 预标注来源（nuscenes / coco / yolo / point）
   */
  private void savePreAnnotation(PreAnnotationAssetVO asset, List<Map<String, Object>> boxes,
                                 String sourceFile, Long userId, String source) {
    Long assetId = asset.getAssetId();
    preAnnotationMapper.softDeletePreAnnotationObjects(assetId, userId);
    preAnnotationMapper.softDeletePreAnnotation(assetId, userId);

    Map<String, Object> header = new HashMap<>();
    header.put("projectId", asset.getProjectId());
    header.put("batchId", asset.getBatchId());
    header.put("uploadId", asset.getUploadId());
    header.put("entryId", asset.getEntryId());
    header.put("assetId", assetId);
    header.put("source", source);
    header.put("sourceFile", trimTo(sourceFile, LIMIT_SOURCE_FILE));
    header.put("sampleToken", boxes.get(0).get("sampleToken"));
    header.put("sensorChannel", boxes.get(0).get("sensorChannel"));
    header.put("boxCount", boxes.size());
    header.put("status", STATUS_READY);
    header.put("userId", userId);
    preAnnotationMapper.insertPreAnnotation(header);
    Long preAnnotationId = asLong(header.get("id"));
    if (preAnnotationId == null) {
      throw new ServiceException("预标注记录写入失败");
    }

    for (Map<String, Object> box : boxes) {
      Map<String, Object> row = new HashMap<>();
      row.put("preAnnotationId", preAnnotationId);
      row.put("assetId", assetId);
      row.put("objectKey", trimTo(String.valueOf(box.get("objectKey")), LIMIT_OBJECT_KEY));
      // 图片格式的框类型由解析器给出（bbox / polygon / keypoint / skeleton），
      // nuScenes 分支的 box 不带该字段，回退为立体框
      Object objectType = box.get("objectType");
      row.put("objectType", objectType == null ? OBJECT_TYPE_CUBOID : String.valueOf(objectType));
      row.put("classId", box.get("classId"));
      // 图片格式的源类别名可能为空，不能落成字符串 "null"
      Object sourceClassName = box.get("sourceClassName");
      row.put("className", sourceClassName == null
          ? null : trimTo(String.valueOf(sourceClassName), LIMIT_CLASS_NAME));
      row.put("geometryJson", box.get("geometryJson"));
      row.put("attrJson", box.get("attrJson"));
      row.put("trackToken", trimTo(String.valueOf(box.get("instanceToken")), LIMIT_TRACK_TOKEN));
      row.put("visibility", box.get("visibility"));
      row.put("userId", userId);
      preAnnotationMapper.insertPreAnnotationObject(row);
    }
  }

  // ---------------- nuScenes 压缩包解析 ----------------

  /** 从压缩包中读取 nuScenes 元数据 JSON（只读白名单内的文件，集齐必需项后提前结束）。 */
  private Map<String, List<Map<String, Object>>> readNuscenesJson(MultipartFile file) {
    Map<String, byte[]> rawBytes = new HashMap<>();
    try (InputStream in = file.getInputStream();
         ZipInputStream zis = new ZipInputStream(new BufferedInputStream(in))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (entry.isDirectory()) {
          continue;
        }
        // 1. 取表名（basename 去 .json），白名单外或已收集则跳过
        String key = NuscenesMetaSupport.jsonKeyOf(entry.getName());
        if (!NuscenesMetaSupport.ACCEPTED_JSON.contains(key) || rawBytes.containsKey(key)) {
          continue;
        }
        // 2. 收集本条目；必需表集齐即提前结束（大包优化）
        rawBytes.put(key, readEntry(zis));
        if (rawBytes.keySet().containsAll(NuscenesMetaSupport.REQUIRED_JSON)) {
          break;
        }
      }
    } catch (IOException e) {
      throw new ServiceException("读取压缩包失败：" + e.getMessage());
    }

    Map<String, List<Map<String, Object>>> parsed = new HashMap<>();
    for (Map.Entry<String, byte[]> e : rawBytes.entrySet()) {
      try {
        parsed.put(e.getKey(), objectMapper.readValue(
            e.getValue(), new TypeReference<List<Map<String, Object>>>() {
            }));
      } catch (IOException ex) {
        throw new ServiceException("解析 " + e.getKey() + ".json 失败，请确认文件格式正确");
      }
    }
    return parsed;
  }

  /** 读取当前 zip 条目内容。 */
  private byte[] readEntry(ZipInputStream zis) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    byte[] chunk = new byte[ENTRY_BUFFER_SIZE];
    int len;
    while ((len = zis.read(chunk)) > 0) {
      buffer.write(chunk, 0, len);
    }
    return buffer.toByteArray();
  }

  // ---------------- 3D 框构建 ----------------

  /** 按路径键（含通道目录）优先、文件名键兜底匹配数据资产，并把该帧的官方 3D 框转换到雷达传感器坐标系。 */
  private List<Map<String, Object>> buildBoxes(NuscenesIndex index, PreAnnotationAssetVO asset) {
    List<Map<String, Object>> boxes = new ArrayList<>();
    String fileKey = NuscenesMetaSupport.dataFileKey(asset.getFileName());
    if (fileKey.isEmpty()) {
      return boxes;
    }
    // 路径键优先：同帧各传感器同名（自采数据集）时精确定位本资产的 sample_data 行，
    // 保证坐标转换用对本传感器的外参（ego_pose/calibrated_sensor）；文件名键兜底（官方数据集）
    Map<String, Object> sampleData = null;
    if (asset.getFileUrl() != null && !asset.getFileUrl().isBlank()) {
      sampleData = index.sampleDataByFileKey.get(NuscenesMetaSupport.dataPathKey(asset.getFileUrl()));
    }
    if (sampleData == null) {
      sampleData = index.sampleDataByFileKey.get(fileKey);
    }
    if (sampleData == null) {
      return boxes;
    }
    String sampleToken = NuscenesMetaSupport.text(sampleData.get("_sampleToken"));
    List<Map<String, Object>> annotations = index.annotationsBySample.get(sampleToken);
    if (annotations == null || annotations.isEmpty()) {
      return boxes;
    }
    Map<String, Object> egoPose = index.egoPoseByToken.get(NuscenesMetaSupport.text(sampleData.get("ego_pose_token")));
    Map<String, Object> calib = index.calibratedSensorByToken.get(NuscenesMetaSupport.text(sampleData.get("calibrated_sensor_token")));
    if (egoPose == null || calib == null) {
      return boxes;
    }

    // 坐标系变换矩阵：global -> ego -> sensor
    double[] egoRotation = NuscenesMetaSupport.quatToMatrix(NuscenesMetaSupport.quat(egoPose.get("rotation")));
    double[] egoTranslation = NuscenesMetaSupport.vec3(egoPose.get("translation"));
    double[] sensorRotation = NuscenesMetaSupport.quatToMatrix(NuscenesMetaSupport.quat(calib.get("rotation")));
    double[] sensorTranslation = NuscenesMetaSupport.vec3(calib.get("translation"));
    String sensorChannel = NuscenesMetaSupport.sensorChannelOf(sampleData, index);

    List<WorkClassVO> classes = preAnnotationMapper.selectProjectClasses(asset.getProjectId());
    if (classes == null) {
      classes = List.of();
    }

    for (Map<String, Object> annotation : annotations) {
      boxes.add(toBox(annotation, index, classes,
          egoRotation, egoTranslation, sensorRotation, sensorTranslation, sampleToken, sensorChannel));
    }
    return boxes;
  }

  /**
   * 累计「实例 -> 缺帧数」。同一个实例在多个资产（帧）里出现，只记一次，
   * 值取该实例计算出的最大缺帧数（几条链分支时以最保守的为准）。
   */
  private void collectInstanceMissing(Map<String, Integer> instanceMissing, Map<String, Object> box) {
    Object token = box.get("instanceToken");
    String instanceKey = NuscenesMetaSupport.text(token);
    if (instanceKey.isEmpty()) {
      return;
    }
    int missing = asInt(box.get("instanceMissingFrames"));
    if (missing <= 0) {
      instanceMissing.putIfAbsent(instanceKey, 0);
      return;
    }
    instanceMissing.merge(instanceKey, missing, Math::max);
  }

  /** 取实例链上第 index 条标注所在的帧序号（从 1 开始）；链为空或没有 sample 表时返回 -1。 */
  private int frameAt(List<Map<String, Object>> chain, int position, NuscenesIndex index) {
    if (chain == null || position < 0 || position >= chain.size()) {
      return -1;
    }
    return NuscenesMetaSupport.frameIndexOf(index, NuscenesMetaSupport.text(chain.get(position).get("sample_token")));
  }

  /** 单个 nuScenes 标注 -> 平台预标注框（几何 + 属性 + 类别映射）。 */
  private Map<String, Object> toBox(Map<String, Object> annotation, NuscenesIndex index,
                                    List<WorkClassVO> classes,
                                    double[] egoRotation, double[] egoTranslation,
                                    double[] sensorRotation, double[] sensorTranslation,
                                    String sampleToken, String sensorChannel) {
    // 位置：global -> ego -> sensor
    double[] globalCenter = NuscenesMetaSupport.vec3(annotation.get("translation"));
    double[] egoCenter = NuscenesMetaSupport.matVecT(egoRotation, NuscenesMetaSupport.sub(globalCenter, egoTranslation));
    double[] sensorCenter = NuscenesMetaSupport.matVecT(sensorRotation, NuscenesMetaSupport.sub(egoCenter, sensorTranslation));

    // 朝向：R_lidar = R_sensor^T · R_ego^T · R_box
    double[] boxRotation = NuscenesMetaSupport.quatToMatrix(NuscenesMetaSupport.quat(annotation.get("rotation")));
    double[] rotation = NuscenesMetaSupport.matMul(NuscenesMetaSupport.matMul(
        NuscenesMetaSupport.transpose(sensorRotation), NuscenesMetaSupport.transpose(egoRotation)), boxRotation);
    double yaw = Math.atan2(rotation[3], rotation[0]);

    // nuScenes size 为 [width, length, height]，平台几何为 [length, width, height]
    double[] size = NuscenesMetaSupport.vec3(annotation.get("size"));
    double length = size[1];
    double width = size[0];
    double height = size[2];

    Map<String, Object> center = new LinkedHashMap<>();
    center.put("x", NuscenesMetaSupport.round4(sensorCenter[0]));
    center.put("y", NuscenesMetaSupport.round4(sensorCenter[1]));
    center.put("z", NuscenesMetaSupport.round4(sensorCenter[2]));
    Map<String, Object> sizeMap = new LinkedHashMap<>();
    sizeMap.put("x", NuscenesMetaSupport.round4(length));
    sizeMap.put("y", NuscenesMetaSupport.round4(width));
    sizeMap.put("z", NuscenesMetaSupport.round4(height));
    Map<String, Object> geometry = new LinkedHashMap<>();
    geometry.put("kind", KIND_CUBOID);
    geometry.put("center", center);
    geometry.put("size", sizeMap);
    geometry.put("rotationZ", NuscenesMetaSupport.round4(yaw));

    String categoryToken = NuscenesMetaSupport.text(annotation.get("category_token"));
    String categoryName = index.categoryNameByToken.getOrDefault(categoryToken, "");
    String instanceToken = NuscenesMetaSupport.text(annotation.get("instance_token"));
    String visibilityToken = NuscenesMetaSupport.text(annotation.get("visibility_token"));
    String visibility = index.visibilityLevelByToken.getOrDefault(visibilityToken, visibilityToken);

    // nuScenes 原生能力接入：attribute（运动/静止）、instance（实例帧范围）、
    // sample_annotation.prev/next（实例链）-> 实例覆盖率与漏标帧数
    List<String> attributeNames = NuscenesMetaSupport.attributeNamesOf(index, annotation);
    String motionStatus = NuscenesMetaSupport.motionStatusOf(attributeNames);
    int frameIndex = NuscenesMetaSupport.frameIndexOf(index, sampleToken);
    Map<String, Object> instanceRow = index.instanceByToken.get(instanceToken);
    List<Map<String, Object>> instanceChain = NuscenesMetaSupport.orderedInstanceAnnotations(index, instanceToken);
    int markedFrames = instanceChain.size();
    int firstFrame = frameAt(instanceChain, 0, index);
    int lastFrame = markedFrames > 1 ? frameAt(instanceChain, markedFrames - 1, index) : firstFrame;
    if (firstFrame > 0 && lastFrame > 0 && lastFrame < firstFrame) {
      int swap = firstFrame;
      firstFrame = lastFrame;
      lastFrame = swap;
    }
    // 官方 instance.nbr_annotations 是这个实例应有的标注总数；
    // 缺失数 = 首末帧跨度 - 本批链上实际条数，用来做漏标检查
    int instanceTotal = instanceRow == null ? -1 : asInt(instanceRow.get("nbr_annotations"));
    int missingFrames = -1;
    if (firstFrame > 0 && lastFrame >= firstFrame) {
      missingFrames = Math.max(0, lastFrame - firstFrame + 1 - markedFrames);
    }

    Map<String, Object> attr = new LinkedHashMap<>();
    attr.put("source", SOURCE_NUSCENES);
    attr.put("sampleToken", sampleToken);
    attr.put("sensorChannel", sensorChannel);
    attr.put("categoryToken", categoryToken);
    attr.put("categoryName", categoryName);
    attr.put("instanceToken", instanceToken);
    attr.put("visibilityToken", visibilityToken);
    attr.put("numLidarPts", asInt(annotation.get("num_lidar_pts")));
    attr.put("numRadarPts", asInt(annotation.get("num_radar_pts")));
    attr.put("globalSize", annotation.get("size"));
    attr.put("globalTranslation", annotation.get("translation"));
    attr.put("globalRotation", annotation.get("rotation"));
    // nuScenes 实例属性与实例链（之前 attribute.json / instance.json / prev 链都没解析，这里补齐）
    attr.put("attributeTokens", annotation.get("attribute_tokens"));
    attr.put("attributeNames", attributeNames);
    attr.put("motionStatus", motionStatus);
    attr.put("frameIndex", frameIndex);
    attr.put("instanceTotalAnnotations", instanceTotal);
    attr.put("instanceMarkedFrames", markedFrames);
    attr.put("instanceFirstFrame", firstFrame);
    attr.put("instanceLastFrame", lastFrame);
    attr.put("instanceMissingFrames", missingFrames);

    String token = NuscenesMetaSupport.text(annotation.get("token"));
    Map<String, Object> box = new HashMap<>();
    box.put("objectKey", token.isEmpty() ? UUID.randomUUID().toString().replace(UUID_SEPARATOR, "") : token);
    box.put("geometryJson", toJson(geometry));
    box.put("attrJson", toJson(attr));
    box.put("sourceClassName", categoryName);
    // nuScenes 类别名是 vehicle.car 这类长名且带层次，保留三级匹配（含末段与包含）
    box.put("classId", matchClassId(classes, categoryName, true));
    box.put("instanceToken", instanceToken);
    box.put("visibility", trimTo(visibility, LIMIT_VISIBILITY));
    box.put("sampleToken", trimTo(sampleToken, LIMIT_SAMPLE_TOKEN));
    box.put("sensorChannel", trimTo(sensorChannel, LIMIT_SENSOR_CHANNEL));
    // 实例维度摘要（只内存流转，用于本次结果的漏标统计，不落库）
    box.put("motionStatus", motionStatus);
    box.put("frameIndex", frameIndex);
    box.put("instanceTotalAnnotations", instanceTotal);
    box.put("instanceMarkedFrames", markedFrames);
    box.put("instanceFirstFrame", firstFrame);
    box.put("instanceLastFrame", lastFrame);
    box.put("instanceMissingFrames", missingFrames);
    return box;
  }

  /**
   * 把源类别名映射到项目标签：完全匹配 -&gt; 末段匹配 -&gt;（可选）包含匹配。
   *
   * @param allowFuzzy 是否启用第三级「双向包含」模糊匹配。
   *     <b>图片格式（COCO / YOLO / 点位）必须传 false</b>：这类数据集的类别名普遍短且彼此相似
   *     （如「水稻田」与「田」、「房屋」与「房屋建筑」），双向包含极易静默错配。
   *     此时宁可匹配不上、在预标注列表里显示「未匹配」由人工指定，也不要把框挂到错误标签下。
   */
  private Long matchClassId(List<WorkClassVO> classes, String sourceName, boolean allowFuzzy) {
    if (classes.isEmpty() || sourceName == null || sourceName.isBlank()) {
      return null;
    }
    String raw = sourceName.trim();
    String normalized = raw.toLowerCase(Locale.ROOT);
    String tail = normalized.contains(".")
        ? normalized.substring(normalized.lastIndexOf('.') + 1) : normalized;
    String head = normalized.contains(".")
        ? normalized.substring(0, normalized.indexOf('.')) : normalized;

    for (WorkClassVO item : classes) {
      if (equalsIgnoreCase(item.getClassName(), raw)) {
        return item.getClassId();
      }
    }
    for (WorkClassVO item : classes) {
      if (equalsIgnoreCase(item.getClassName(), tail)) {
        return item.getClassId();
      }
    }
    // 图片格式到此为止：再往下就是双向包含，短类别名极易误命中
    if (!allowFuzzy) {
      return null;
    }
    for (WorkClassVO item : classes) {
      String name = item.getClassName() == null ? "" : item.getClassName().trim().toLowerCase(Locale.ROOT);
      if (name.isEmpty()) {
        continue;
      }
      if (name.contains(tail) || tail.contains(name) || name.contains(head) || head.contains(name)) {
        return item.getClassId();
      }
    }
    return null;
  }

  // ---------------- 小工具 ----------------

  private boolean equalsIgnoreCase(String left, String right) {
    return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
  }

  private String trimTo(String value, int max) {
    if (value == null) {
      return null;
    }
    return value.length() > max ? value.substring(0, max) : value;
  }
}
