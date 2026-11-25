package org.dromara.label.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.label.dto.AnnotationObjectDTO;
import org.dromara.label.dto.CommentAddDTO;
import org.dromara.label.dto.SequenceAnnotationSaveDTO;
import org.dromara.label.mapper.SequenceMapper;
import org.dromara.label.service.BaseService;
import org.dromara.label.service.MinioService;
import org.dromara.label.service.SequenceService;
import org.dromara.label.vo.AnnotationErrorVO;
import org.dromara.label.vo.AnnotationObjectVO;
import org.dromara.label.vo.ClassRefVO;
import org.dromara.label.vo.CommentVO;
import org.dromara.label.vo.FrameVO;
import org.dromara.label.vo.IdVO;
import org.dromara.label.vo.PrecheckVO;
import org.dromara.label.vo.SavedCountVO;
import org.dromara.label.vo.SensorDataVO;
import org.dromara.label.vo.SensorVO;
import org.dromara.label.vo.SequenceAnnotationVO;
import org.dromara.label.vo.SequenceFrameVO;
import org.dromara.label.vo.SequenceImportVO;
import org.dromara.label.vo.SequenceVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 多传感器多帧序列数据服务（阶段1）。
 *
 * <p>负责 nuScenes 序列导入（解析 v1.0-trainval 元数据 JSON、建立
 * sequence/frame/sensor/calibration/ego_pose/sensor_data 关联链、传感器文件入库 MinIO），
 * 以及序列/帧/传感器数据的查询。SQL 统一收敛至 SequenceMapper.xml。</p>
 */
@Service
public class SequenceServiceImpl extends BaseService implements SequenceService {

  @Autowired
  private SequenceMapper sequenceMapper;

  @Autowired
  private MinioService minioService;

  // ---------------- 查询 ----------------

  @Override
  public List<SequenceVO> sequences(Long projectId, Long batchId) {
    return sequenceMapper.selectSequences(projectId, batchId);
  }

  @Override
  public SequenceVO sequence(Long id) {
    SequenceVO sequence = sequenceMapper.selectSequence(id);
    if (sequence == null) {
      throw new ServiceException("序列不存在");
    }
    return sequence;
  }

  @Override
  public List<FrameVO> frames(Long sequenceId) {
    return sequenceMapper.selectFrames(sequenceId);
  }

  @Override
  public List<SensorDataVO> frameSensorData(Long frameId) {
    return sequenceMapper.selectSensorDataByFrame(frameId);
  }

  @Override
  public List<SensorVO> sensors(Long projectId) {
    return sequenceMapper.selectSensors(projectId);
  }

  // ---------------- 导入 ----------------

  /**
   * 导入 nuScenes 序列压缩包。
   *
   * <p>压缩包须为 nuScenes 结构：v1.0-trainval/*.json 元数据 + samples/* 传感器文件。
   * 先落 MinIO 临时对象（zip 需二次遍历），再解析建链。</p>
   */
  @Override
  @Transactional
  public SequenceImportVO importNuscenesSequence(Long projectId, Long batchId, MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new ServiceException("上传文件不能为空");
    }
    Long targetBatchId = resolveBatchId(projectId, batchId);
    String objectName =
        "imports/sequence/" + projectId + "/" + System.currentTimeMillis() + ".zip";
    try {
      minioService.putObject(objectName, file.getInputStream(), file.getSize(), "application/zip");
    } catch (IOException e) {
      throw new IllegalStateException("读取上传文件失败：" + e.getMessage(), e);
    }
    try {
      return importNuscenesFromObject(projectId, targetBatchId, objectName);
    } finally {
      // 导入完成后清理临时 zip（MinIO 中已存放传感器文件本体）
      try {
        minioService.removeObject(objectName);
      } catch (Exception ignored) {
        // 忽略清理失败
      }
    }
  }

  @Override
  @Transactional
  public SequenceImportVO importNuscenesFromObject(Long projectId, Long batchId, String zipObjectName) {
    Long targetBatchId = resolveBatchId(projectId, batchId);
    Meta meta = readNuscenesMeta(zipObjectName);
    long userId = currentUserId();

    // 1. 传感器（复用已存在的同 channel）
    Map<String, Long> sensorIdByToken = new HashMap<>();
    for (Map<String, Object> sensor : meta.sensors) {
      String channel = str(sensor.get("channel"));
      Long existing = sequenceMapper.selectSensorId(projectId, channel);
      long id;
      if (existing != null) {
        id = existing;
      } else {
        Map<String, Object> p = new HashMap<>();
        p.put("projectId", projectId);
        p.put("channel", channel);
        p.put("modality", str(sensor.get("modality")));
        p.put("userId", userId);
        sequenceMapper.insertSensor(p);
        id = asLong(p.get("id"));
      }
      sensorIdByToken.put(str(sensor.get("token")), id);
    }

    // 2. 标定
    Map<String, Long> calibIdByToken = new HashMap<>();
    for (Map<String, Object> calib : meta.calibratedSensors) {
      Long sensorId = sensorIdByToken.get(str(calib.get("sensor_token")));
      Map<String, Object> p = new HashMap<>();
      p.put("sensorId", sensorId);
      p.put("token", str(calib.get("token")));
      p.put("translation", toJson(calib.get("translation")));
      p.put("rotation", toJson(calib.get("rotation")));
      p.put("cameraIntrinsic", toJson(calib.get("camera_intrinsic")));
      p.put("userId", userId);
      sequenceMapper.insertCalibration(p);
      calibIdByToken.put(str(calib.get("token")), asLong(p.get("id")));
    }

    // 3. 位姿
    Map<String, Long> poseIdByToken = new HashMap<>();
    for (Map<String, Object> pose : meta.egoPoses) {
      Map<String, Object> p = new HashMap<>();
      p.put("token", str(pose.get("token")));
      p.put("translation", toJson(pose.get("translation")));
      p.put("rotation", toJson(pose.get("rotation")));
      p.put("timestampUs", longVal(pose.get("timestamp")));
      p.put("userId", userId);
      sequenceMapper.insertEgoPose(p);
      poseIdByToken.put(str(pose.get("token")), asLong(p.get("id")));
    }

    // 4. 序列（场景），查重避免重复导入
    Map<String, Long> seqIdByToken = new HashMap<>();
    for (Map<String, Object> scene : meta.scenes) {
      String token = str(scene.get("token"));
      Long existing = sequenceMapper.selectSequenceIdByToken(projectId, targetBatchId, token);
      if (existing != null) {
        seqIdByToken.put(token, existing);
        continue;
      }
      Map<String, Object> p = new HashMap<>();
      p.put("projectId", projectId);
      p.put("batchId", targetBatchId);
      p.put("token", token);
      p.put("name", str(scene.get("name")));
      p.put("description", str(scene.get("description")));
      p.put("frameCount", intVal(scene.get("nbr_samples")));
      p.put("userId", userId);
      sequenceMapper.insertSequence(p);
      seqIdByToken.put(token, asLong(p.get("id")));
    }

    // 5. 帧（按场景分组、按时间排序分配帧序号）
    Map<String, List<Map<String, Object>>> samplesByScene = new HashMap<>();
    for (Map<String, Object> sample : meta.samples) {
      samplesByScene
          .computeIfAbsent(str(sample.get("scene_token")), k -> new ArrayList<>())
          .add(sample);
    }
    Map<String, Long> frameIdByToken = new HashMap<>();
    for (Map.Entry<String, List<Map<String, Object>>> entry : samplesByScene.entrySet()) {
      Long seqId = seqIdByToken.get(entry.getKey());
      if (seqId == null) {
        continue;
      }
      List<Map<String, Object>> samples = entry.getValue();
      samples.sort(Comparator.comparingLong(s -> longVal(s.get("timestamp"))));
      int index = 1;
      Long prevId = null;
      for (Map<String, Object> sample : samples) {
        Map<String, Object> p = new HashMap<>();
        p.put("sequenceId", seqId);
        p.put("token", str(sample.get("token")));
        p.put("frameIndex", index++);
        p.put("timestampUs", longVal(sample.get("timestamp")));
        p.put("prevId", prevId);
        p.put("userId", userId);
        sequenceMapper.insertFrame(p);
        long frameId = asLong(p.get("id"));
        if (prevId != null) {
          sequenceMapper.updateFrameNext(prevId, frameId);
        }
        frameIdByToken.put(str(sample.get("token")), frameId);
        prevId = frameId;
      }
    }

    // 6. 序列首末帧
    for (Map<String, Object> scene : meta.scenes) {
      Long seqId = seqIdByToken.get(str(scene.get("token")));
      if (seqId == null) {
        continue;
      }
      sequenceMapper.updateSequenceFirstLast(
          seqId,
          frameIdByToken.get(str(scene.get("first_sample_token"))),
          frameIdByToken.get(str(scene.get("last_sample_token"))));
    }

    // 7. 传感器数据（sample_data）
    int dataCount = 0;
    for (Map<String, Object> sd : meta.sampleDatas) {
      Long frameId = frameIdByToken.get(str(sd.get("sample_token")));
      Long calibId = calibIdByToken.get(str(sd.get("calibrated_sensor_token")));
      if (frameId == null || calibId == null) {
        continue;
      }
      Long sensorId = sequenceMapper.selectCalibrationSensorId(calibId);
      String relPath = str(sd.get("filename"));
      String fileName = relPath.contains("/") ? relPath.substring(relPath.lastIndexOf('/') + 1) : relPath;
      String fileUrl = "/uploads/" + projectId + "/sequence/" + relPath;
      Map<String, Object> p = new HashMap<>();
      p.put("frameId", frameId);
      p.put("sensorId", sensorId);
      p.put("calibrationId", calibId);
      p.put("egoPoseId", poseIdByToken.get(str(sd.get("ego_pose_token"))));
      p.put("fileName", fileName);
      p.put("fileUrl", fileUrl);
      p.put("fileFormat", str(sd.get("fileformat")));
      p.put("width", intVal(sd.get("width")));
      p.put("height", intVal(sd.get("height")));
      p.put("isKeyFrame", boolInt(sd.get("is_key_frame")));
      p.put("metaJson", toJson(sd));
      p.put("userId", userId);
      sequenceMapper.insertSensorData(p);
      dataCount++;
    }

    // 8. 传感器文件入库 MinIO（第二次遍历 zip，只处理 samples/sweeps 文件）
    uploadSampleFiles(zipObjectName, projectId);

    // 9. 批次状态置为已解析
    labelCommonMapper.touchAssetBatch(targetBatchId);

    SequenceImportVO result = new SequenceImportVO();
    result.setSequenceCount(meta.scenes.size());
    result.setSensorCount(sensorIdByToken.size());
    result.setFrameCount(frameIdByToken.size());
    result.setSensorDataCount(dataCount);
    return result;
  }

  // ---------------- 序列标注 ----------------

  /** 序列标注视图：序列 + 帧（含传感器数据） + 标签 + 每帧标注对象。 */
  @Override
  public SequenceAnnotationVO annotationOfSequence(Long sequenceId) {
    SequenceVO sequence = sequence(sequenceId);
    Long projectId = sequence.getProjectId();
    List<FrameVO> frameList = frames(sequenceId);
    List<SequenceFrameVO> enriched = new ArrayList<>();
    Map<String, Object> annotations = new HashMap<>();
    for (FrameVO frame : frameList) {
      SequenceFrameVO item = new SequenceFrameVO();
      item.setId(frame.getId());
      item.setSequenceId(frame.getSequenceId());
      item.setFrameToken(frame.getFrameToken());
      item.setFrameIndex(frame.getFrameIndex());
      item.setTimestampUs(frame.getTimestampUs());
      item.setPrevFrameId(frame.getPrevFrameId());
      item.setNextFrameId(frame.getNextFrameId());
      item.setIsKeyFrame(frame.getIsKeyFrame());
      item.setSensorData(frameSensorData(frame.getId()));
      enriched.add(item);
      annotations.put("frame-" + frame.getId(), mapOf("objects", objectsOfFrame(frame.getId())));
    }
    SequenceAnnotationVO result = new SequenceAnnotationVO();
    result.setSequence(sequence);
    result.setProjectId(projectId);
    result.setFrames(enriched);
    result.setClasses(classesOfProject(projectId));
    result.setAnnotations(annotations);
    return result;
  }

  /** 保存某帧标注（含追踪实例维护）。 */
  @Override
  @Transactional
  public SavedCountVO saveSequenceAnnotation(Long sequenceId, SequenceAnnotationSaveDTO dto) {
    Long frameId = dto.getFrameId();
    Long projectId = sequence(sequenceId).getProjectId();
    long userId = currentUserId();

    Long annotationId = sequenceMapper.selectAnnotationId(sequenceId, frameId);
    if (annotationId == null) {
      Map<String, Object> p = new HashMap<>();
      p.put("sequenceId", sequenceId);
      p.put("frameId", frameId);
      p.put("userId", userId);
      sequenceMapper.insertAnnotation(p);
      annotationId = asLong(p.get("id"));
    } else {
      sequenceMapper.touchAnnotation(annotationId, userId);
    }

    sequenceMapper.deleteAnnotationObjects(annotationId);

    List<AnnotationObjectDTO> objects = dto.getObjects();
    if (objects == null || objects.isEmpty()) {
      return new SavedCountVO(0, annotationId);
    }
    // 全帧去重：同一帧内按标识（objectKey > trackToken > 几何+类别+类型签名）去重，
    // 防止逐帧复制 / 回退在同一帧生成重复框、造成实例 id 漂移（一个框在同一帧不能重复框）。
    List<AnnotationObjectDTO> deduped = dedupeFrameObjects(objects);
    int count = 0;
    for (AnnotationObjectDTO item : deduped) {
      persistObject(annotationId, frameId, projectId, item);
      count++;
    }
    return new SavedCountVO(count, annotationId);
  }

  /**
   * 同一帧内去重：objectKey 优先，其次 trackToken（跨帧实例标识），
   * 二者皆缺时退化为「几何 + 类别 + 类型」签名兜底。
   * 保证「一个框在同一帧不能重复框」，避免逐帧回退形成两个框、实例 id 漂移。
   */
  private List<AnnotationObjectDTO> dedupeFrameObjects(List<AnnotationObjectDTO> objects) {
    LinkedHashMap<String, AnnotationObjectDTO> unique = new LinkedHashMap<>();
    for (AnnotationObjectDTO obj : objects) {
      unique.put(frameObjectIdentity(obj), obj);
    }
    return new ArrayList<>(unique.values());
  }

  private String frameObjectIdentity(AnnotationObjectDTO obj) {
    String objectKey = obj.getObjectKey();
    if (objectKey != null && !objectKey.trim().isEmpty()) {
      return "K:" + objectKey.trim();
    }
    String trackToken = obj.getTrackToken();
    if (trackToken != null && !trackToken.trim().isEmpty()) {
      return "T:" + trackToken.trim();
    }
    Object geometry = firstPresent(obj.getGeometry(), obj.getGeometryJson());
    String geometryJson = geometry == null ? "" : toJson(geometry);
    return "G:" + obj.getType() + ":" + obj.getClassId() + ":" + geometryJson;
  }

  private List<ClassRefVO> classesOfProject(Long projectId) {
    return sequenceMapper.selectClassesOfProject(projectId);
  }

  private void persistObject(
      Long annotationId, Long frameId, Long projectId, AnnotationObjectDTO obj) {
    String objectKey = obj.getObjectKey();
    if (objectKey == null || objectKey.isEmpty()) {
      objectKey = "obj-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 10000);
    }
    String trackToken = obj.getTrackToken();
    Long instanceId = null;
    if (trackToken != null && !trackToken.isEmpty()) {
      instanceId = ensureInstance(projectId, trackToken, obj.getClassId(), frameId);
    }
    String geometry = toJson(firstPresent(obj.getGeometry(), obj.getGeometryJson()));
    String attrs = toJson(firstPresent(obj.getAttributes(), obj.getAttrJson()));
    Map<String, Object> p = new HashMap<>();
    p.put("annotationId", annotationId);
    p.put("frameId", frameId);
    p.put("objectKey", objectKey);
    p.put("type", obj.getType());
    p.put("classId", obj.getClassId());
    p.put("geometry", geometry);
    p.put("attrs", attrs);
    p.put("trackToken", trackToken);
    p.put("instanceId", instanceId);
    p.put("visibility", obj.getVisibility());
    p.put("userId", currentUserId());
    sequenceMapper.insertAnnotationObject(p);
  }

  /** 确保追踪实例存在（按 project + trackToken），返回 instanceId。 */
  private Long ensureInstance(Long projectId, String trackToken, Long classId, Long frameId) {
    Long existing = sequenceMapper.selectInstanceId(projectId, trackToken);
    if (existing != null) {
      if (frameId != null) {
        sequenceMapper.touchInstance(existing, frameId);
      }
      return existing;
    }
    Map<String, Object> p = new HashMap<>();
    p.put("projectId", projectId);
    p.put("instanceToken", trackToken);
    p.put("classId", classId);
    p.put("firstFrameId", frameId);
    p.put("lastFrameId", frameId);
    p.put("userId", currentUserId());
    sequenceMapper.insertInstance(p);
    return asLong(p.get("id"));
  }

  private List<AnnotationObjectVO> objectsOfFrame(Long frameId) {
    Long annotationId = sequenceMapper.selectLatestAnnotationIdByFrame(frameId);
    if (annotationId == null) {
      return List.of();
    }
    return sequenceMapper.selectObjectsOfAnnotation(annotationId);
  }

  // ---------------- 审核（错误/前检/批注） ----------------

  @Override
  public List<AnnotationErrorVO> annotationErrors(Long sequenceId) {
    return sequenceMapper.selectAnnotationErrors(sequenceId);
  }

  @Override
  public List<PrecheckVO> prechecks(Long sequenceId) {
    return sequenceMapper.selectPrechecks(sequenceId);
  }

  @Override
  public List<CommentVO> comments(Long sequenceId) {
    return sequenceMapper.selectComments(sequenceId);
  }

  @Override
  public IdVO addComment(Long sequenceId, CommentAddDTO dto) {
    Map<String, Object> p = new HashMap<>();
    p.put("sequenceId", sequenceId);
    p.put("frameId", dto.getFrameId());
    p.put("node", dto.getNode() == null ? "review" : dto.getNode());
    p.put("content", dto.getContent());
    p.put("creatorId", currentUserId());
    sequenceMapper.insertComment(p);
    return new IdVO(asLong(p.get("id")));
  }

  @Override
  public void correctError(Long errorId) {
    sequenceMapper.correctError(errorId);
  }

  @Override
  public void correctComment(Long commentId) {
    sequenceMapper.correctComment(commentId);
  }

  /** 简化前检：检查某帧对象是否缺失标签类别，返回本次检测出的错误项。 */
  @Override
  @Transactional
  public List<PrecheckVO> runPrecheck(Long sequenceId, Long frameId) {
    sequenceMapper.deletePrechecks(sequenceId, frameId);
    List<PrecheckVO> result = new ArrayList<>();
    for (AnnotationObjectVO object : objectsOfFrame(frameId)) {
      if (object.getClassId() == null) {
        Map<String, Object> p = new HashMap<>();
        p.put("sequenceId", sequenceId);
        p.put("frameId", frameId);
        p.put("objectKey", object.getObjectKey());
        p.put("checkType", "missing_class");
        p.put("message", "对象未选择标签类别");
        p.put("userId", currentUserId());
        sequenceMapper.insertPrecheck(p);
        PrecheckVO vo = new PrecheckVO();
        vo.setId(asLong(p.get("id")));
        vo.setSequenceId(sequenceId);
        vo.setFrameId(frameId);
        vo.setObjectKey(object.getObjectKey());
        vo.setCheckType("missing_class");
        vo.setMessage("对象未选择标签类别");
        result.add(vo);
      }
    }
    return result;
  }

  // ---------------- 内部工具 ----------------

  private void uploadSampleFiles(String zipObjectName, Long projectId) {
    int count = 0;
    try (InputStream in = minioService.openObject(zipObjectName);
        ZipInputStream zip = new ZipInputStream(in)) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (entry.isDirectory()) {
          continue;
        }
        String name = entry.getName();
        int samplesIdx = name.indexOf("samples/");
        if (samplesIdx < 0) {
          samplesIdx = name.indexOf("sweeps/");
        }
        if (samplesIdx < 0) {
          continue;
        }
        String rel = name.substring(samplesIdx);
        String fileUrl = "/uploads/" + projectId + "/sequence/" + rel;
        minioService.putObject(fileUrl.substring(1), zip, -1L, minioService.contentTypeFor(name));
        count++;
      }
    } catch (IOException e) {
      throw new IllegalStateException("传感器文件入库失败：" + e.getMessage(), e);
    }
    if (count == 0) {
      throw new IllegalStateException("压缩包中未找到 samples/sweeps 传感器文件");
    }
  }

  private Meta readNuscenesMeta(String zipObjectName) {
    Meta meta = new Meta();
    try (InputStream in = minioService.openObject(zipObjectName);
        ZipInputStream zip = new ZipInputStream(in)) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (entry.isDirectory()) {
          continue;
        }
        String name = entry.getName();
        if (!name.endsWith(".json") || !name.contains("v1.0-trainval/")) {
          continue;
        }
        String file = name.substring(name.lastIndexOf('/') + 1);
        String key = file.substring(0, file.length() - 5);
        List<Map<String, Object>> list =
            objectMapper.readValue(zip, new TypeReference<List<Map<String, Object>>>() {});
        switch (key) {
          case "sensor" -> meta.sensors = list;
          case "scene" -> meta.scenes = list;
          case "sample" -> meta.samples = list;
          case "sample_data" -> meta.sampleDatas = list;
          case "calibrated_sensor" -> meta.calibratedSensors = list;
          case "ego_pose" -> meta.egoPoses = list;
          default -> {
            // category/attribute/instance/sample_annotation 等阶段2+使用，暂忽略
          }
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("解析 nuScenes 元数据失败：" + e.getMessage(), e);
    }
    if (meta.sensors.isEmpty() || meta.scenes.isEmpty() || meta.samples.isEmpty()) {
      throw new IllegalStateException("压缩包缺少必要的 nuScenes 元数据（sensor/scene/sample）");
    }
    return meta;
  }

  private static final class Meta {
    List<Map<String, Object>> sensors = List.of();
    List<Map<String, Object>> scenes = List.of();
    List<Map<String, Object>> samples = List.of();
    List<Map<String, Object>> sampleDatas = List.of();
    List<Map<String, Object>> calibratedSensors = List.of();
    List<Map<String, Object>> egoPoses = List.of();
  }

  private static String str(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private static long longVal(Object value) {
    return value == null ? 0L : ((Number) value).longValue();
  }

  private static int intVal(Object value) {
    return value == null ? 0 : ((Number) value).intValue();
  }

  private static int boolInt(Object value) {
    return value == null || !Boolean.parseBoolean(String.valueOf(value)) ? 0 : 1;
  }

  private static Map<String, Object> mapOf(Object... kv) {
    Map<String, Object> map = new HashMap<>();
    for (int i = 0; i + 1 < kv.length; i += 2) {
      map.put(String.valueOf(kv[i]), kv[i + 1]);
    }
    return map;
  }
}
