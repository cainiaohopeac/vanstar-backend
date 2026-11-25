package org.dromara.label.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.label.common.AnnotationStatus;
import org.dromara.label.common.LabelJobStatus;
import org.dromara.label.common.MediaType;
import org.dromara.label.common.TextNodeType;
import org.dromara.label.common.TextSentimentLevel;
import org.dromara.label.dto.TextAnnotationSaveDTO;
import org.dromara.label.dto.TextEntitySaveDTO;
import org.dromara.label.dto.TextSentimentSaveDTO;
import org.dromara.label.entity.LabelAnnotation;
import org.dromara.label.entity.LabelAsset;
import org.dromara.label.entity.LabelDataEntry;
import org.dromara.label.entity.LabelJob;
import org.dromara.label.entity.LabelTextClass;
import org.dromara.label.entity.LabelTextEntity;
import org.dromara.label.entity.LabelTextSentiment;
import org.dromara.label.mapper.AnnotationMapper;
import org.dromara.label.mapper.TextAnnotationMapper;
import org.dromara.label.mapper.TextClassMapper;
import org.dromara.label.service.BaseService;
import org.dromara.label.service.MinioService;
import org.dromara.label.service.TextAnnotationService;
import org.dromara.label.vo.JobEntryProgressVO;
import org.dromara.label.vo.TextAnnotationSaveResultVO;
import org.dromara.label.vo.TextAnnotationVO;
import org.dromara.label.vo.TextAssetVO;
import org.dromara.label.vo.TextClassVO;
import org.dromara.label.vo.TextSentenceVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文本标注的加载与保存。
 *
 * <p>存储要点：
 * <ul>
 *   <li>位置是<strong>全文级字符偏移</strong>（start 闭、end 开），与 {@code substring} 一致；</li>
 *   <li>保存是<strong>全量语义</strong>——先软删该存档下的旧实体与情感，再整体写入。
 *       一篇文本通常只有几十到几百个实体，全量重建的成本远低于维护增量明细的复杂度，
 *       也不会出现「前端删了但没同步」的脏数据；</li>
 *   <li>写入走批量（多行 VALUES），避免上百次数据库往返。</li>
 * </ul>
 */
@Service
public class TextAnnotationServiceImpl extends BaseService implements TextAnnotationService {

  /** 批量写入每批的行数（与视觉标注对象保持一致）。 */
  private static final int BATCH_SIZE = 500;

  private final TextAnnotationMapper textAnnotationMapper;
  private final TextClassMapper textClassMapper;
  private final AnnotationMapper annotationMapper;
  private final MinioService minioService;
  private final ObjectMapper objectMapper;

  public TextAnnotationServiceImpl(TextAnnotationMapper textAnnotationMapper,
      TextClassMapper textClassMapper, AnnotationMapper annotationMapper,
      MinioService minioService, ObjectMapper objectMapper) {
    this.textAnnotationMapper = textAnnotationMapper;
    this.textClassMapper = textClassMapper;
    this.annotationMapper = annotationMapper;
    this.minioService = minioService;
    this.objectMapper = objectMapper;
  }

  // ---------------- 加载 ----------------

  @Override
  public TextAnnotationVO view(Long jobId, Long assetId) {
    if (jobId == null || assetId == null) {
      throw new ServiceException("jobId 与 assetId 不能为空");
    }
    LabelAsset asset = requireTextAsset(assetId);

    TextAnnotationVO vo = new TextAnnotationVO();
    vo.setJobId(jobId);
    vo.setAssetId(assetId);
    vo.setProjectId(asset.getProjectId());
    vo.setFileName(asset.getFileName());
    vo.setText(readText(asset.getFileUrl()));

    List<int[]> bounds = parseBounds(asset.getMetaJson());
    vo.setCharCount(parseCharCount(asset.getMetaJson(), vo.getText()));
    vo.setSentences(toSentenceVOs(bounds));

    LabelAnnotation annotation = textAnnotationMapper.selectAnnotation(jobId, assetId);
    if (annotation != null) {
      vo.setAnnotationId(annotation.getId());
      vo.setStatus(annotation.getStatus());
      vo.setEntities(textAnnotationMapper.selectEntities(annotation.getId()));
      vo.setSentiments(textAnnotationMapper.selectSentiments(annotation.getId()));
      vo.setComment(extractComment(annotation.getAnnotationJson()));
    } else {
      vo.setEntities(List.of());
      vo.setSentiments(List.of());
    }
    vo.setClasses(buildClassTree(textClassMapper.selectClasses(asset.getProjectId())));
    return vo;
  }

  @Override
  public List<TextAssetVO> jobAssets(Long jobId) {
    if (jobId == null) {
      throw new ServiceException("jobId 不能为空");
    }
    return textAnnotationMapper.selectJobTextAssets(jobId);
  }

  // ---------------- 保存 ----------------

  @Override
  @Transactional
  public TextAnnotationSaveResultVO save(TextAnnotationSaveDTO dto) {
    if (dto == null || dto.getJobId() == null || dto.getAssetId() == null) {
      throw new ServiceException("jobId 与 assetId 不能为空");
    }
    long userId = currentUserId();
    LabelAsset asset = requireTextAsset(dto.getAssetId());

    // 句边界与字数从资产元信息取：校验偏移不需要回读正文，省一次 MinIO 往返与大文本传输
    List<int[]> bounds = parseBounds(asset.getMetaJson());
    int charCount = parseCharCount(asset.getMetaJson(), null);
    Map<Long, LabelTextClass> classMap = loadClassMap(asset.getProjectId());

    List<LabelTextEntity> entityRows =
        buildEntityRows(dto.getEntities(), classMap, bounds, charCount, userId);
    List<LabelTextSentiment> sentimentRows =
        buildSentimentRows(dto.getSentiments(), classMap, bounds.size(), userId);

    boolean submit = LabelJobStatus.PENDING_REVIEW.equals(dto.getStatus());
    String annotationStatus = submit ? AnnotationStatus.SUBMITTED : AnnotationStatus.DRAFT;

    LabelAnnotation annotation = textAnnotationMapper.selectAnnotation(dto.getJobId(), dto.getAssetId());
    if (annotation == null) {
      annotation = new LabelAnnotation();
      annotation.setJobId(dto.getJobId());
      annotation.setAssetId(dto.getAssetId());
      annotation.setAnnotatorId(userId);
      annotation.setStatus(annotationStatus);
      annotation.setAnnotationJson(buildAnnotationJson(dto.getComment()));
      annotation.setCreateBy(userId);
      annotation.setUpdateBy(userId);
      textAnnotationMapper.insertAnnotation(annotation);
    } else {
      annotation.setStatus(annotationStatus);
      annotation.setAnnotationJson(buildAnnotationJson(dto.getComment()));
      annotation.setUpdateBy(userId);
      textAnnotationMapper.updateAnnotation(annotation);
    }

    // 全量语义：先软删旧的，再整体写入
    textAnnotationMapper.softDeleteEntities(annotation.getId(), userId);
    textAnnotationMapper.softDeleteSentiments(annotation.getId(), userId);
    for (LabelTextEntity row : entityRows) {
      row.setAnnotationId(annotation.getId());
    }
    for (LabelTextSentiment row : sentimentRows) {
      row.setAnnotationId(annotation.getId());
    }
    batchInsertEntities(entityRows);
    batchInsertSentiments(sentimentRows);

    // 实体批注级联清理：全量替换后，entityKey 不在提交集合里的实例批注失去宿主，一并软删
    //（复用图片/点云的框级批注表，objectKey = 文本实体 entityKey，批注随之清理口径一致）
    Map<String, Object> purge = new HashMap<>();
    purge.put("jobId", dto.getJobId());
    purge.put("assetIds", List.of(dto.getAssetId()));
    purge.put("objectKeys", entityRows.stream().map(LabelTextEntity::getEntityKey).toList());
    purge.put("userId", userId);
    annotationMapper.purgeOrphanObjectComments(purge);

    TextAnnotationSaveResultVO result = new TextAnnotationSaveResultVO();
    result.setAnnotationId(annotation.getId());
    result.setStatus(annotationStatus);
    result.setEntityCount(entityRows.size());
    result.setSentimentCount(sentimentRows.size());
    if (submit) {
      result.setJobStatus(advanceSubmitStatus(dto.getJobId(), dto.getAssetId(), userId));
    }
    return result;
  }

  // ---------------- 实体行构造与校验 ----------------

  /**
   * 把入参实体转成待写入的行，并逐条校验。
   *
   * <p>不做实体之间的互斥校验：<strong>允许嵌套</strong>（如「北京市」中嵌套「北京」），
   * 因此只校验每个区间自身合法且不跨句。
   */
  private List<LabelTextEntity> buildEntityRows(List<TextEntitySaveDTO> dtoList,
      Map<Long, LabelTextClass> classMap, List<int[]> bounds, int charCount, long userId) {
    List<LabelTextEntity> rows = new ArrayList<>();
    if (dtoList == null || dtoList.isEmpty()) {
      return rows;
    }
    for (TextEntitySaveDTO item : dtoList) {
      LabelTextClass klass = classMap.get(item.getClassId());
      if (klass == null || !TextNodeType.ENTITY.equals(klass.getNodeType())) {
        throw new ServiceException("实体标签不存在或类型不匹配：" + item.getClassId());
      }
      Integer start = item.getStartOffset();
      Integer end = item.getEndOffset();
      if (start == null || end == null || start < 0 || end <= start) {
        throw new ServiceException("实体偏移不合法：[" + start + "," + end + ")");
      }
      if (charCount > 0 && end > charCount) {
        throw new ServiceException("实体偏移超出正文长度：" + end + " > " + charCount);
      }
      Integer sentenceIndex = locateSentence(bounds, start, end);
      if (sentenceIndex == null) {
        throw new ServiceException("实体不得跨句：[" + start + "," + end + ")");
      }
      LabelTextEntity row = new LabelTextEntity();
      row.setEntityKey(item.getEntityKey());
      row.setClassId(item.getClassId());
      row.setStartOffset(start);
      row.setEndOffset(end);
      row.setEntityText(item.getEntityText());
      // 以服务端定位的句序为准，不信任前端传入值（避免与句边界不一致）
      row.setSentenceIndex(sentenceIndex);
      row.setCreateBy(userId);
      row.setUpdateBy(userId);
      rows.add(row);
    }
    return rows;
  }

  /**
   * 定位实体所属句子；若区间跨越了句边界则返回 null（当前业务不允许跨句）。
   */
  private Integer locateSentence(List<int[]> bounds, int start, int end) {
    for (int i = 0; i < bounds.size(); i++) {
      int[] bound = bounds.get(i);
      if (start >= bound[0] && end <= bound[1]) {
        return i;
      }
    }
    return null;
  }

  // ---------------- 情感行构造与校验 ----------------

  private List<LabelTextSentiment> buildSentimentRows(List<TextSentimentSaveDTO> dtoList,
      Map<Long, LabelTextClass> classMap, int sentenceCount, long userId) {
    List<LabelTextSentiment> rows = new ArrayList<>();
    if (dtoList == null || dtoList.isEmpty()) {
      return rows;
    }
    for (TextSentimentSaveDTO item : dtoList) {
      LabelTextClass klass = classMap.get(item.getClassId());
      if (klass == null || !TextNodeType.CLASSIFICATION.equals(klass.getNodeType())) {
        throw new ServiceException("情感标签不存在或类型不匹配：" + item.getClassId());
      }
      boolean sentenceLevel = TextSentimentLevel.SENTENCE.equals(item.getLevelType());
      boolean entryLevel = TextSentimentLevel.ENTRY.equals(item.getLevelType());
      if (!sentenceLevel && !entryLevel) {
        throw new ServiceException("情感粒度不合法：" + item.getLevelType());
      }
      if (sentenceLevel) {
        Integer index = item.getSentenceIndex();
        if (index == null || index < 0 || index >= sentenceCount) {
          throw new ServiceException("句序号越界：" + index);
        }
      }
      LabelTextSentiment row = new LabelTextSentiment();
      row.setLevelType(item.getLevelType());
      row.setSentenceIndex(sentenceLevel ? item.getSentenceIndex() : null);
      row.setClassId(item.getClassId());
      row.setRemark(item.getRemark());
      row.setCreateBy(userId);
      row.setUpdateBy(userId);
      rows.add(row);
    }
    return rows;
  }

  // ---------------- 批量写入 ----------------

  private void batchInsertEntities(List<LabelTextEntity> rows) {
    for (int i = 0; i < rows.size(); i += BATCH_SIZE) {
      textAnnotationMapper.insertEntityRows(rows.subList(i, Math.min(rows.size(), i + BATCH_SIZE)));
    }
  }

  private void batchInsertSentiments(List<LabelTextSentiment> rows) {
    for (int i = 0; i < rows.size(); i += BATCH_SIZE) {
      textAnnotationMapper.insertSentimentRows(
          rows.subList(i, Math.min(rows.size(), i + BATCH_SIZE)));
    }
  }

  // ---------------- 提交审核的状态推进 ----------------

  /**
   * 提交审核：条目内数据随条目流转，作业进度按条目聚合，全部提交后才进入「待审核」。
   *
   * <p>与视觉标注的处理保持一致，避免同一套审核流程出现两套状态语义。
   */
  private String advanceSubmitStatus(Long jobId, Long assetId, long userId) {
    Long entryId = textAnnotationMapper.selectEntryIdByAssetId(assetId);
    if (entryId != null) {
      String current = textAnnotationMapper.selectEntryLabelStatus(entryId);
      boolean revised = current != null
          && (LabelEntryStatuses.REVISING.equals(current)
          || LabelEntryStatuses.REVISED.equals(current)
          || LabelEntryStatuses.UNREVISED.equals(current));
      String labelStatus = revised ? LabelEntryStatuses.REVISED : LabelEntryStatuses.LABELED;
      LabelDataEntry entry = new LabelDataEntry();
      entry.setId(entryId);
      entry.setLabelStatus(labelStatus);
      entry.setUpdateBy(userId);
      textAnnotationMapper.updateEntryLabelStatus(entry);
    }
    LabelAsset asset = new LabelAsset();
    asset.setId(assetId);
    asset.setUpdateBy(userId);
    textAnnotationMapper.updateAssetSubmitStatus(asset);

    JobEntryProgressVO progress = textAnnotationMapper.selectJobEntryProgress(jobId);
    long total = progress == null || progress.getTotal() == null ? 0L : progress.getTotal();
    long done = progress == null || progress.getDone() == null ? 0L : progress.getDone();
    // 无条目归属时按本次提交即完成处理
    boolean jobSubmitted = total == 0 || done >= total;
    String jobStatus = jobSubmitted ? LabelJobStatus.PENDING_REVIEW : LabelJobStatus.LABELING;
    int percent = total > 0 ? (int) (done * 100 / total) : 100;

    LabelJob job = new LabelJob();
    job.setId(jobId);
    job.setStatus(jobStatus);
    job.setProgress(percent);
    job.setUpdateBy(userId);
    textAnnotationMapper.updateJobProgress(job);
    return jobStatus;
  }

  /** 条目标注状态取值（与 label_data_entry.label_status 保持一致）。 */
  private static final class LabelEntryStatuses {
    private static final String LABELED = "labeled";
    private static final String REVISED = "revised";
    private static final String REVISING = "revising";
    private static final String UNREVISED = "unrevised";
  }

  // ---------------- 辅助 ----------------

  /** 校验资产存在且确为文本类型，避免误用图片/点云接口的地址。 */
  private LabelAsset requireTextAsset(Long assetId) {
    LabelAsset asset = textAnnotationMapper.selectAsset(assetId);
    if (asset == null) {
      throw new ServiceException("数据不存在：" + assetId);
    }
    if (!MediaType.TEXT.equals(asset.getMediaType())) {
      throw new ServiceException("该数据不是文本类型，无法用文本标注打开");
    }
    return asset;
  }

  /** 读取正文全文。 */
  private String readText(String fileUrl) {
    if (fileUrl == null || fileUrl.isEmpty()) {
      return "";
    }
    try (InputStream in = minioService.openObject(fileUrl.startsWith("/") ? fileUrl.substring(1) : fileUrl)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new ServiceException("正文读取失败：" + e.getMessage());
    }
  }

  /** 解析标签列表为 id → 标签 的映射，供保存时校验标签归属与类型。 */
  private Map<Long, LabelTextClass> loadClassMap(Long projectId) {
    return textClassMapper.selectClasses(projectId).stream()
        .collect(Collectors.toMap(LabelTextClass::getId, Function.identity(), (a, b) -> a));
  }

  /** 平铺标签列表组装成树（一次查询 + 内存建树，避免按层递归查询）。 */
  private List<TextClassVO> buildClassTree(List<LabelTextClass> classes) {
    Map<Long, TextClassVO> byId = new HashMap<>();
    List<TextClassVO> roots = new ArrayList<>();
    for (LabelTextClass klass : classes) {
      TextClassVO vo = new TextClassVO();
      vo.setId(klass.getId());
      vo.setParentId(klass.getParentId());
      vo.setNodeType(klass.getNodeType());
      vo.setLabelName(klass.getLabelName());
      vo.setColor(klass.getColor());
      vo.setSortOrder(klass.getSortOrder());
      vo.setChildren(new ArrayList<>());
      byId.put(klass.getId(), vo);
    }
    for (LabelTextClass klass : classes) {
      TextClassVO vo = byId.get(klass.getId());
      TextClassVO parent = klass.getParentId() == null ? null : byId.get(klass.getParentId());
      if (parent == null) {
        roots.add(vo);
      } else {
        parent.getChildren().add(vo);
      }
    }
    return roots;
  }

  /** 句边界元信息：{@code bounds} 是扁平数组 [s0,e0,s1,e1,...]。 */
  private List<int[]> parseBounds(String metaJson) {
    List<int[]> bounds = new ArrayList<>();
    if (metaJson == null || metaJson.isBlank()) {
      return bounds;
    }
    try {
      JsonNode root = objectMapper.readTree(metaJson);
      JsonNode array = root.get("bounds");
      if (array == null || !array.isArray()) {
        return bounds;
      }
      for (int i = 0; i + 1 < array.size(); i += 2) {
        bounds.add(new int[] {array.get(i).asInt(), array.get(i + 1).asInt()});
      }
    } catch (IOException e) {
      throw new ServiceException("句边界元信息解析失败：" + e.getMessage());
    }
    return bounds;
  }

  /**
   * 取字数：优先用元信息里记录的 charCount，缺失时按实际正文长度兜底。
   * 保存路径不读正文，因此这里允许正文为空时返回 0（此时跳过越界校验）。
   */
  private int parseCharCount(String metaJson, String text) {
    if (metaJson != null && !metaJson.isBlank()) {
      try {
        JsonNode root = objectMapper.readTree(metaJson);
        JsonNode node = root.get("charCount");
        if (node != null && node.isNumber()) {
          return node.asInt();
        }
      } catch (IOException e) {
        // 元信息损坏时回退到按正文计算，不阻断标注
      }
    }
    return text == null ? 0 : text.length();
  }

  private List<TextSentenceVO> toSentenceVOs(List<int[]> bounds) {
    List<TextSentenceVO> vos = new ArrayList<>(bounds.size());
    for (int i = 0; i < bounds.size(); i++) {
      TextSentenceVO vo = new TextSentenceVO();
      vo.setIndex(i);
      vo.setStart(bounds.get(i)[0]);
      vo.setEnd(bounds.get(i)[1]);
      vos.add(vo);
    }
    return vos;
  }

  /** 存档 JSON 只放批注；实体与情感都有独立表，不塞进 JSON 以免重复存储。 */
  private String buildAnnotationJson(String comment) {
    try {
      return objectMapper.writeValueAsString(Map.of("comment", comment == null ? "" : comment));
    } catch (IOException e) {
      return "{\"comment\":\"\"}";
    }
  }

  private String extractComment(String annotationJson) {
    if (annotationJson == null || annotationJson.isBlank()) {
      return "";
    }
    try {
      JsonNode node = objectMapper.readTree(annotationJson).get("comment");
      return node == null ? "" : node.asText("");
    } catch (IOException e) {
      return "";
    }
  }
}
