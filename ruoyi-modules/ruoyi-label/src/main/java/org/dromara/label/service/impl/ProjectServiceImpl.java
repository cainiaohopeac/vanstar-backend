package org.dromara.label.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.label.common.AssetMetaSource;
import org.dromara.label.common.AssetParseStatus;
import org.dromara.label.common.LabelRoleConstants;
import org.dromara.label.common.MediaType;
import org.dromara.label.common.PageResult;
import org.dromara.label.common.ProjectStatus;
import org.dromara.label.common.UploadDataStatus;
import org.dromara.label.common.UploadSourceType;
import org.dromara.label.common.ImageDimensionUtil;
import org.dromara.label.dto.BatchSaveDTO;
import org.dromara.label.dto.ClassAttributeDTO;
import org.dromara.label.dto.ClassDTO;
import org.dromara.label.dto.ClassSetDTO;
import org.dromara.label.dto.EntrySplitDTO;
import org.dromara.label.dto.NuscenesArchiveRequest;
import org.dromara.label.dto.NuscenesParseDTO;
import org.dromara.label.dto.ProjectAssetUploadDTO;
import org.dromara.label.dto.ProjectSaveDTO;
import org.dromara.label.mapper.ProjectMapper;
import org.dromara.label.service.BaseService;
import org.dromara.label.service.MinioService;
import org.dromara.label.service.NuscenesImportService;
import org.dromara.label.service.NuscenesMetaCollector;
import org.dromara.label.service.ProjectService;
import org.dromara.label.service.WorkflowService;
import org.dromara.label.vo.AssetBatchVO;
import org.dromara.label.vo.AssetVO;
import org.dromara.label.vo.ClassAttributeVO;
import org.dromara.label.vo.NuscenesParseResultVO;
import org.dromara.label.vo.ClassSetVO;
import org.dromara.label.vo.DataEntryVO;
import org.dromara.label.vo.ClassVO;
import org.dromara.label.vo.ProjectMemberVO;
import org.dromara.label.vo.ProjectVO;
import org.dromara.label.vo.UploadDataVO;
import org.dromara.label.vo.UploadResultVO;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 项目域服务：项目生命周期（创建/编辑/启动/完成/归档/恢复/复制）、
 * 项目成员、标签体系（类别集/类别）、资产数据与批次、zip/文件上传与入库。
 *
 * <p>继承 {@link BaseService} 复用公共能力，SQL 统一收敛至 ProjectMapper.xml。</p>
 */
@Service
public class ProjectServiceImpl extends BaseService implements ProjectService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProjectServiceImpl.class);

  /** 读取图片像素尺寸（宽/高）；fileUrl 非网关路径或非图片时返回 null（尺寸缺省 0）。 */
  private int[] readImageSize(String fileUrl) {
    if (fileUrl == null || !fileUrl.startsWith("/uploads/")) {
      return null;
    }
    try (InputStream in = minioService.openObject(fileUrl.substring(1))) {
      return ImageDimensionUtil.read(in);
    } catch (Exception e) {
      LOGGER.warn("读取图片尺寸失败（尺寸将置 0）：{}", fileUrl, e);
      return null;
    }
  }

  /** 元数据归档上传网络类失败的自动重试总次数（含首次尝试） */
  private static final int ARCHIVE_RETRY_MAX_ATTEMPTS = 3;

  @Autowired
  private MinioService minioService;

  @Autowired
  private ProjectMapper projectMapper;

  @Autowired
  private WorkflowService workflowService;

  /** nuScenes 元数据归档与自动解析（导入链路） */
  @Autowired
  private NuscenesImportService nuscenesImportService;

  // ---------------- 项目 ----------------

  @Override
  public PageResult<ProjectVO> projects(String keyword, String status, String dataType, boolean mine) {
    // mine=true：仅当前用户创建的项目（导出等场景的项目下拉）
    Long createdBy = mine ? currentUserId() : null;
    List<ProjectVO> list = projectMapper.selectProjects(keyword, status, dataType, createdBy);
    fillProjectTeams(list);
    return PageResult.of(list);
  }

  @Override
  public ProjectVO project(Long id) {
    ProjectVO row = projectMapper.selectProjectById(id);
    if (row == null) {
      throw new ServiceException("项目不存在或已归档");
    }
    fillProjectTeam(row);
    return row;
  }

  /** 归档项目（归档即软删）：仅已完成的项目可归档；归档后可在「已归档」视图恢复。 */
  @Override
  @Transactional
  public void archiveProject(Long id) {
    String status = currentProjectStatus(id);
    if (ProjectStatus.ARCHIVED.equals(status)) {
      return;
    }
    if (!ProjectStatus.COMPLETED.equals(status)) {
      throw new ServiceException("仅已完成的项目可以归档");
    }
    projectMapper.archiveProject(id, currentUserId());
    writeOperationLog("ARCHIVE_PROJECT", "project", id, Map.of("projectId", id));
  }

  /** 恢复已归档项目：从「已归档」回到「已完成」。 */
  @Override
  @Transactional
  public ProjectVO restoreProject(Long id) {
    String status = currentProjectStatus(id);
    if (!ProjectStatus.ARCHIVED.equals(status)) {
      throw new ServiceException("仅已归档的项目可以恢复");
    }
    projectMapper.restoreProject(id, currentUserId());
    writeOperationLog("RESTORE_PROJECT", "project", id, Map.of("projectId", id));
    return project(id);
  }

  /** 复制项目：以现有项目为模板复制其基础信息、成员与标签体系，创建新项目（状态为 created）。 */
  @Override
  @Transactional
  public ProjectVO copyProject(Long sourceId) {
    Map<String, Object> source = projectMapper.selectProjectRow(sourceId);
    if (source == null) {
      throw new ServiceException("源项目不存在或已归档");
    }
    long userId = currentUserId();
    String projectCode = "PRJ-" + LocalDateTime.now().getYear() + "-" + nextCode("label_project");
    Map<String, Object> p = new HashMap<>();
    p.put("projectCode", projectCode);
    p.put("projectName", stringValue(source.get("project_name")) + "-副本");
    p.put("description", source.get("description"));
    p.put("dataType", source.get("data_type"));
    p.put("ownerId", source.get("owner_id"));
    p.put("teamId", source.get("team_id"));
    p.put("status", ProjectStatus.CREATED);
    p.put("userId", userId);
    projectMapper.insertProject(p);
    long newId = asLong(p.get("id"));

    // 项目成员由绑定团队自动派生，无需复制成员表，仅需复制队伍绑定
    // 复制多队伍绑定：优先取 label_project_team 关联，存量老数据回退主队伍 team_id
    List<Long> relTeamIds = projectMapper.selectProjectTeamIds(sourceId);
    List<Long> copiedTeams = validTeamIds(relTeamIds);
    if (copiedTeams.isEmpty()) {
      Long primaryTeamId = asLong(source.get("team_id"));
      copiedTeams = primaryTeamId == null ? List.of() : List.of(primaryTeamId);
    }
    syncProjectTeams(newId, copiedTeams);
    // 复制项目同样初始化默认工作流配置（后续可在「工作流配置」中调整）
    workflowService.initDefault(newId);
    // 复制标签集与标签（不含标注数据/任务/资产，保持项目为待配置状态）
    for (ClassSetVO classSet : projectMapper.selectClassSets(sourceId)) {
      Map<String, Object> cs = new HashMap<>();
      cs.put("projectId", newId);
      cs.put("name", classSet.getName());
      cs.put("version", classSet.getVersion());
      cs.put("userId", userId);
      projectMapper.insertClassSet(cs);
      long newClassSetId = asLong(cs.get("id"));
      for (ClassVO clazz : projectMapper.selectClasses(classSet.getId())) {
        Map<String, Object> c = new HashMap<>();
        c.put("classSetId", newClassSetId);
        c.put("labelName", clazz.getLabelName());
        c.put("color", clazz.getColor());
        c.put("toolType", clazz.getToolType());
        c.put("defaultSizeL", clazz.getDefaultSizeL());
        c.put("defaultSizeW", clazz.getDefaultSizeW());
        c.put("defaultSizeH", clazz.getDefaultSizeH());
        c.put("minInsidePoints", clazz.getMinInsidePoints());
        c.put("sortOrder", clazz.getSortOrder());
        c.put("userId", userId);
        projectMapper.insertClass(c);
      }
    }
    writeOperationLog("COPY_PROJECT", "project", newId, Map.of("sourceId", sourceId));
    return project(newId);
  }

  /**
   * 启动项目：按「参与团队 → 标注标签 → 标注数据 → 团队成员」的顺序逐项校验，
   * 全部齐备后才置为进行中；任一项缺失都抛出可读的提示，避免启动后无法下发任务。
   */
  @Override
  @Transactional
  public ProjectVO startProject(Long id) {
    String status = currentProjectStatus(id);
    if (ProjectStatus.RUNNING.equals(status)) {
      return project(id);
    }
    if (ProjectStatus.COMPLETED.equals(status) || ProjectStatus.ARCHIVED.equals(status)) {
      throw new ServiceException("已完成或已归档的项目不能启动");
    }
    if (projectMapper.selectProjectTeamIds(id).isEmpty()) {
      throw new ServiceException("启动前请先在「项目信息」中配置参与团队");
    }
    // 文本项目的标签体系在 label_text_class（与视觉不共用），按项目类型分流校验；
    // 文本标签允许不挂集直接挂项目，故查标签表而非集表
    long classSetCount = MediaType.TEXT.equals(projectMapper.selectProjectDataType(id))
        ? projectMapper.countTextClassByProject(id)
        : projectMapper.countClassSetByProject(id);
    if (classSetCount == 0) {
      throw new ServiceException("启动前请先在「标注标签」中配置标签集及标注标签");
    }
    if (projectMapper.countAssetByProject(id) == 0) {
      throw new ServiceException("启动前请先在「数据管理」中上传标注数据");
    }
    if (projectMembers(id).isEmpty()) {
      throw new ServiceException("启动前请先为参与团队维护团队成员，否则无人可以领取标注");
    }
    projectMapper.startProject(id, currentUserId());
    writeOperationLog("START_PROJECT", "project", id, Map.of("projectId", id));
    return project(id);
  }

  /** 完成项目：仅进行中的项目可完成，记录结束时间。 */
  @Override
  @Transactional
  public ProjectVO completeProject(Long id) {
    String status = currentProjectStatus(id);
    if (ProjectStatus.COMPLETED.equals(status)) {
      return project(id);
    }
    if (!ProjectStatus.RUNNING.equals(status)) {
      throw new ServiceException("仅进行中的项目可以完成");
    }
    projectMapper.completeProject(id, currentUserId());
    writeOperationLog("COMPLETE_PROJECT", "project", id, Map.of("projectId", id));
    return project(id);
  }

  private String currentProjectStatus(Long id) {
    String status = projectMapper.selectProjectStatus(id);
    return status == null ? "" : status;
  }

  /** 上传 zip 压缩包并解析其中的图片/点云文件，批量写入数据对象。
   * <p>解压后若第一层包含多个条目（多个顶层目录/文件，且非 nuScenes 数据集），
   * 则每个顶层条目各自成为一条上传数据与数据条目；否则整体作为一条上传数据
   * （兼容 nuScenes 数据集与普通单目录压缩包）。 */
  @Override
  public UploadResultVO uploadProjectZip(Long projectId, Long batchId, MultipartFile file, Integer framesPerEntry) {
    if (file == null || file.isEmpty()) {
      throw new ServiceException("上传文件不能为空");
    }
    String originalName = sanitizeAssetFileName(file.getOriginalFilename());
    if (originalName == null || !originalName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
      throw new ServiceException("仅支持 zip 压缩包");
    }
    int fpe = normalizeFramesPerEntry(framesPerEntry);
    Long targetBatchId = resolveBatchId(projectId, batchId);
    // 先探测条目名编码（GBK 包自动回退），全链路按同一编码打开，避免半路 MalformedInputException
    Charset nameCharset = null;
    try {
      nameCharset = detectZipCharset(file::getInputStream);
    } catch (IOException ignored) {
      // 探测自身 IO 失败：按默认编码继续，后续正式扫描会对同一 IO 问题抛「解析 zip 失败」
    }
    ZipTopology topology;
    try (InputStream scan = file.getInputStream()) {
      topology = scanZipTopology(scan, nameCharset);
    } catch (IOException e) {
      throw new IllegalStateException("解析 zip 失败：" + e.getMessage(), e);
    }
    if (!topology.innerZips.isEmpty()) {
      try (InputStream in = file.getInputStream()) {
        return ingestZipOfZips(projectId, targetBatchId, in, originalName, topology, fpe, nameCharset);
      } catch (IOException e) {
        throw new IllegalStateException("解析 zip 失败：" + e.getMessage(), e);
      }
    }
    if (topology.nuscenes || topology.segments.size() <= 1) {
      Long uploadId =
          createUploadData(projectId, targetBatchId, originalName, file.getSize(), null, UploadSourceType.ZIP);
      try (ZipInputStream zip = openZip(file.getInputStream(), nameCharset)) {
        return ingestZip(projectId, targetBatchId, uploadId, zip, originalName, fpe, nameCharset);
      } catch (IOException e) {
        throw new IllegalStateException("解析 zip 失败：" + e.getMessage(), e);
      }
    }
    try (InputStream in = file.getInputStream()) {
      return ingestZipSplit(projectId, targetBatchId, in, originalName, topology, fpe, nameCharset);
    } catch (IOException e) {
      throw new IllegalStateException("解析 zip 失败：" + e.getMessage(), e);
    }
  }

  /**
   * 解析 zip 流中的图片/点云文件，写入 MinIO 并批量创建数据对象。
   * <p>
   * MinIO 为唯一文件源：zip 条目流式直传（大小未知时 size=-1 由 SDK 自动分片，
   * 内存仅缓冲单个分片），全程不落本地磁盘；上传后回查对象大小写入资产记录。
   */
  /** zip 压缩包入库（无路径前缀），兼容 nuScenes 自动解析。 */
  private UploadResultVO ingestZip(
      Long projectId,
      Long batchId,
      Long uploadId,
      InputStream source,
      String sourceName,
      int framesPerEntry,
      Charset nameCharset) {
    return ingestZip(projectId, batchId, uploadId, source, sourceName, "", framesPerEntry, nameCharset);
  }

  /** zip 压缩包入库，可指定 MinIO 对象路径前缀（用于 zip 里套 zip 时隔离各内层压缩包）。 */
  private UploadResultVO ingestZip(
      Long projectId,
      Long batchId,
      Long uploadId,
      InputStream source,
      String sourceName,
      String pathPrefix,
      int framesPerEntry,
      Charset nameCharset) {
    int imageCount = 0;
    int pointCloudCount = 0;
    int savedCount = 0;
    int skippedCount = 0;
    List<Long> savedAssetIds = new ArrayList<>();
    String prefix = (pathPrefix == null) ? "" : pathPrefix;
    if (!prefix.isEmpty() && !prefix.endsWith("/")) {
      prefix = prefix + "/";
    }
    // nuScenes 元数据流式收集器：解压扫描时收集 v1.0-* 下的 JSON（不入资产库），
    // 上传结束后归档到 MinIO 并自动解析（点云-相机图关联 + 预标注）
    NuscenesMetaCollector metaCollector =
        nuscenesImportService.newCollector(buildMetaArchiveRequest(projectId, batchId, uploadId, sourceName));
    try (ZipInputStream zip = openZip(source, nameCharset)) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (entry.isDirectory()) {
          continue;
        }
        String name = entry.getName();
        String mediaType = mediaTypeOf(name);
        if (mediaType == null) {
          // 非媒体文件：交给元数据收集器（nuScenes v1.0-* JSON），仍计入跳过数
          metaCollector.collect(name, zip);
          skippedCount++;
          continue;
        }
        Path rel = Path.of(name).normalize();
        if (rel.isAbsolute() || rel.getNameCount() == 0 || rel.startsWith("..")) {
          skippedCount++; // 防 zip slip：拒绝绝对路径与目录穿越
          continue;
        }
        String relPath = rel.toString().replace('\\', '/');
        String fileUrl = "/uploads/" + projectId + "/data/" + prefix + relPath;
        String objectKey = fileUrl.substring(1);
        // 流式直传 MinIO（对象大小未知 -> SDK 自动分片），zip 解压全程不落本地磁盘
        minioService.putObject(objectKey, zip, -1L, minioService.contentTypeFor(name));
        long size = 0L;
        var info = minioService.statObject(objectKey);
        if (info != null) {
          size = info.size();
        }
        String fileName = rel.getFileName().toString();
        Map<String, Object> a = new HashMap<>();
        a.put("projectId", projectId);
        a.put("batchId", batchId);
        a.put("uploadId", uploadId);
        a.put("mediaType", mediaType);
        a.put("fileName", fileName);
        a.put("fileUrl", fileUrl);
        a.put("thumbnailUrl", MediaType.IMAGE.equals(mediaType) ? fileUrl : null);
        a.put("fileSize", size);
        int[] dim = readImageSize(fileUrl);
        a.put("width", dim == null ? 0 : dim[0]);
        a.put("height", dim == null ? 0 : dim[1]);
        a.put("metaJson", toJson(Map.of("source", AssetMetaSource.ZIP_UPLOAD, "zipEntry", name)));
        a.put("userId", currentUserId());
        projectMapper.insertAsset(a);
        projectMapper.bumpAssetBatch(batchId);
        savedAssetIds.add(asLong(a.get("id")));
        if (MediaType.IMAGE.equals(mediaType)) {
          imageCount++;
        } else {
          pointCloudCount++;
        }
        savedCount++;
      }
    } catch (IOException e) {
      throw new IllegalStateException("解析 zip 失败：" + e.getMessage(), e);
    }
    if (uploadId != null) {
      projectMapper.refreshUploadAssetCount(uploadId);
      // 点云与纯图片上传均按帧数上限自动拆分为多个标注条目；帧数可在导入时配置（默认 60）
      attachOrAutoSplitEntry(uploadId, savedAssetIds, framesPerEntry);
    }
    // 检测到 nuScenes 元数据时自动解析：归档上传 MinIO 后建点云-相机图关联并预标注
    autoParseNuscenesMeta(metaCollector, uploadId);
    writeOperationLog(
        "UPLOAD_ZIP",
        "project",
        projectId,
        Map.of("fileName", sourceName, "savedCount", savedCount));
    UploadResultVO result = new UploadResultVO();
    result.setSavedCount(savedCount);
    result.setImageCount(imageCount);
    result.setPointCloudCount(pointCloudCount);
    result.setSkippedCount(skippedCount);
    result.setUploadId(uploadId);
    return result;
  }

  /** zip 解压后的顶层结构：是否 nuScenes 数据集、各顶层条目（顶层目录/文件）及顶层内嵌 zip。 */
  private static final class ZipTopology {
    boolean nuscenes = false;
    /** 顶层分段 → 该段内媒体文件的累计大小（只统计能入库的图片 / 点云） */
    final LinkedHashMap<String, Long> segments = new LinkedHashMap<>();
    final LinkedHashMap<String, Long> innerZips = new LinkedHashMap<>();
  }

  /**
   * zip 条目名回退编码：Windows 压缩软件常用 GBK 打包（条目名非 UTF-8），默认按 UTF-8
   * 解码条目名会在 getNextEntry 阶段直接抛 MalformedInputException——上游表现为
   * 「/import/direct 发生未知异常」，整个文件入库失败。遇到这种包自动按 GBK 重开。
   */
  private static final Charset ZIP_FALLBACK_CHARSET = Charset.forName("GBK");

  /** 可重复打开输入流的回调：编码探测需要把流从头空扫一遍，调用方必须支持重复打开 */
  @FunctionalInterface
  private interface ZipStreamOpener {
    InputStream get() throws IOException;
  }

  /** 判断异常链中是否包含 zip 条目名解码失败（MalformedInputException）。 */
  private static boolean isZipNameDecodingError(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof MalformedInputException) {
        return true;
      }
      if (t.getCause() == t) {
        break;
      }
    }
    return false;
  }

  /**
   * 探测 zip 条目名编码：按默认 UTF-8 空扫一遍条目头（不解压内容），
   * 扫完 = UTF-8 包（返回 null，用默认编码）；中途抛解码异常 = 条目名非 UTF-8（返回 GBK）。
   */
  private Charset detectZipCharset(ZipStreamOpener opener) throws IOException {
    try (ZipInputStream zip = new ZipInputStream(opener.get())) {
      while (zip.getNextEntry() != null) {
        // 只读条目名，不消费条目内容
      }
      return null;
    } catch (IOException e) {
      if (isZipNameDecodingError(e)) {
        return ZIP_FALLBACK_CHARSET;
      }
      throw e;
    }
  }

  /** 按指定编码打开 zip（条目名解码用）；charset 为 null 时用默认 UTF-8。 */
  private static ZipInputStream openZip(InputStream in, Charset nameCharset) {
    return nameCharset == null ? new ZipInputStream(in) : new ZipInputStream(in, nameCharset);
  }

  /** 扫描 zip 顶层结构：统计各顶层分段/内嵌 zip 的媒体文件与累计大小，并探测是否为 nuScenes 数据集。 */
  private ZipTopology scanZipTopology(InputStream source, Charset nameCharset) throws IOException {
    ZipTopology topology = new ZipTopology();
    try (ZipInputStream zip = openZip(source, nameCharset)) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (entry.isDirectory()) {
          continue;
        }
        String name = entry.getName();
        if (!topology.nuscenes && isNuscenesMetaName(name)) {
          topology.nuscenes = true;
        }
        if (isRootZipEntry(name)) {
          long size = entry.getSize() < 0 ? 0L : entry.getSize();
          topology.innerZips.merge(name, size, Long::sum);
          continue;
        }
        String seg = topSegment(name);
        if (seg == null) {
          continue;
        }
        // 只有能被识别为图片 / 点云的条目才计入分段。纯标签与配置文件
        // （如 YOLO 包的 class.json、各类 .txt 标签、.DS_Store / __MACOSX 残留）
        // 本身建不出数据资产，若也让它们撑出一个顶层分段，就会为这个段单独
        // 创建一条上传记录，最终留下「资产 0、条目 0」的空记录。
        if (mediaTypeOf(name) == null) {
          continue;
        }
        long size = entry.getSize() < 0 ? 0L : entry.getSize();
        topology.segments.merge(seg, size, Long::sum);
      }
    }
    return topology;
  }

  /** 判断 zip 条目是否为根目录下的 .zip 文件（压缩包里套压缩包）。 */
  private boolean isRootZipEntry(String name) {
    String n = name;
    while (n.startsWith("/")) {
      n = n.substring(1);
    }
    return n.toLowerCase(Locale.ROOT).endsWith(".zip") && !n.contains("/");
  }

  /** 根目录零散文件统一归入的顶层分段名（整包作为一条上传数据，后续按 60 帧自动拆条目）。 */
  private static final String ROOT_FILES_SEGMENT = "__root_files__";

  /**
   * 取 zip 条目的顶层分段：根目录下的子目录以其目录名作为分段（每个目录各一条上传数据）；
   * 根目录下的零散文件（无子目录层级）统一归入 {@link #ROOT_FILES_SEGMENT}，
   * 整包作为一条上传数据后由 autoSplitEntriesByFrameLimit 按帧数上限（点云 60/条）自动拆条目，
   * 避免「一个文件一个上传/条目」。
   */
  private String topSegment(String name) {
    String n = name;
    while (n.startsWith("/")) {
      n = n.substring(1);
    }
    if (n.isEmpty()) {
      return null;
    }
    int i = n.indexOf('/');
    if (i < 0) {
      return ROOT_FILES_SEGMENT;
    }
    return n.substring(0, i);
  }

  /** 探测 nuScenes 元数据文件（v1.0-* 目录下的 JSON），此类包整体作为一条上传数据，不做拆分。 */
  private boolean isNuscenesMetaName(String name) {
    String lower = name.toLowerCase(Locale.ROOT);
    return lower.contains("v1.0-") && lower.endsWith(".json");
  }

  /**
   * 按顶层条目拆分入库：每个顶层目录各建一条上传数据，并各自按帧数上限自动拆条目；
   * 根目录下的零散文件统一归入「根目录文件」段（整包一条上传数据）。
   * 非媒体文件（如 nuScenes 之外的附属文件）计入跳过数；nuScenes 包已在调用方判断为整体入库，不会进入本方法。
   */
  private UploadResultVO ingestZipSplit(
      Long projectId,
      Long batchId,
      InputStream source,
      String sourceName,
      ZipTopology topology,
      int framesPerEntry,
      Charset nameCharset) {
    Map<String, Long> segUploadId = new LinkedHashMap<>();
    for (Map.Entry<String, Long> en : topology.segments.entrySet()) {
      String uploadName =
          ROOT_FILES_SEGMENT.equals(en.getKey()) ? (sourceName + "（根目录文件）") : en.getKey();
      Long uid =
          createUploadData(projectId, batchId, uploadName, en.getValue(), null, UploadSourceType.ZIP);
      segUploadId.put(en.getKey(), uid);
    }
    int imageCount = 0;
    int pointCloudCount = 0;
    int savedCount = 0;
    int skippedCount = 0;
    Map<String, List<Long>> segAssets = new LinkedHashMap<>();
    Map<String, Integer> segPointCloud = new LinkedHashMap<>();
    for (String seg : topology.segments.keySet()) {
      segAssets.put(seg, new ArrayList<>());
      segPointCloud.put(seg, 0);
    }
    try (ZipInputStream zip = openZip(source, nameCharset)) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (entry.isDirectory()) {
          continue;
        }
        String name = entry.getName();
        String mediaType = mediaTypeOf(name);
        if (mediaType == null) {
          skippedCount++;
          continue;
        }
        String seg = topSegment(name);
        Long uploadId = segUploadId.get(seg);
        if (uploadId == null) {
          skippedCount++;
          continue;
        }
        Path rel = Path.of(name).normalize();
        if (rel.isAbsolute() || rel.getNameCount() == 0 || rel.startsWith("..")) {
          skippedCount++; // 防 zip slip：拒绝绝对路径与目录穿越
          continue;
        }
        String relPath = rel.toString().replace('\\', '/');
        String fileUrl = "/uploads/" + projectId + "/data/" + relPath;
        String objectKey = fileUrl.substring(1);
        minioService.putObject(objectKey, zip, -1L, minioService.contentTypeFor(name));
        long size = 0L;
        var info = minioService.statObject(objectKey);
        if (info != null) {
          size = info.size();
        }
        String fileName = rel.getFileName().toString();
        Map<String, Object> a = new HashMap<>();
        a.put("projectId", projectId);
        a.put("batchId", batchId);
        a.put("uploadId", uploadId);
        a.put("mediaType", mediaType);
        a.put("fileName", fileName);
        a.put("fileUrl", fileUrl);
        a.put("thumbnailUrl", MediaType.IMAGE.equals(mediaType) ? fileUrl : null);
        a.put("fileSize", size);
        int[] dim = readImageSize(fileUrl);
        a.put("width", dim == null ? 0 : dim[0]);
        a.put("height", dim == null ? 0 : dim[1]);
        a.put("metaJson", toJson(Map.of("source", AssetMetaSource.ZIP_UPLOAD, "zipEntry", name)));
        a.put("userId", currentUserId());
        projectMapper.insertAsset(a);
        projectMapper.bumpAssetBatch(batchId);
        segAssets.get(seg).add(asLong(a.get("id")));
        if (MediaType.IMAGE.equals(mediaType)) {
          imageCount++;
        } else {
          pointCloudCount++;
          segPointCloud.put(seg, segPointCloud.get(seg) + 1);
        }
        savedCount++;
      }
    } catch (IOException e) {
      throw new IllegalStateException("解析 zip 失败：" + e.getMessage(), e);
    }
    Long firstUploadId = null;
    for (Map.Entry<String, Long> en : segUploadId.entrySet()) {
      Long uid = en.getValue();
      if (firstUploadId == null) {
        firstUploadId = uid;
      }
      projectMapper.refreshUploadAssetCount(uid);
      // 点云与纯图片分段均按帧数上限自动拆分；帧数可在导入时配置（默认 60）
      attachOrAutoSplitEntry(uid, segAssets.get(en.getKey()), framesPerEntry);
    }
    writeOperationLog(
        "UPLOAD_ZIP_SPLIT",
        "project",
        projectId,
        Map.of("fileName", sourceName, "uploadCount", segUploadId.size(), "savedCount", savedCount));
    UploadResultVO result = new UploadResultVO();
    result.setSavedCount(savedCount);
    result.setImageCount(imageCount);
    result.setPointCloudCount(pointCloudCount);
    result.setSkippedCount(skippedCount);
    result.setUploadId(firstUploadId);
    return result;
  }

  /**
   * 处理“zip 里套 zip”的合集：外层 zip 顶层每个 .zip 文件各自建一条上传数据，
   * 并调用标准 zip 入库逻辑（含 nuScenes 自动解析）处理其内部数据。
   */
  private UploadResultVO ingestZipOfZips(
      Long projectId,
      Long batchId,
      InputStream source,
      String sourceName,
      ZipTopology topology,
      int framesPerEntry,
      Charset nameCharset) {
    int imageCount = 0;
    int pointCloudCount = 0;
    int savedCount = 0;
    int skippedCount = 0;
    Long firstUploadId = null;
    int uploadCount = 0;
    try (ZipInputStream outer = openZip(source, nameCharset)) {
      ZipEntry entry;
      while ((entry = outer.getNextEntry()) != null) {
        if (entry.isDirectory()) {
          continue;
        }
        String name = entry.getName();
        if (!isRootZipEntry(name)) {
          skippedCount++;
          continue;
        }
        Long size = entry.getSize() < 0 ? 0L : entry.getSize();
        Long uploadId =
            createUploadData(projectId, batchId, name, size, null, UploadSourceType.ZIP);
        uploadCount++;
        if (firstUploadId == null) {
          firstUploadId = uploadId;
        }
        // 内层 zip 的条目流不可重开（来自外层解压流），GBK 条目名没法在线探测重试——
        // 先落到临时文件（处理完即删），就能先探测编码再按正确编码打开入库
        Path innerTmp = Files.createTempFile("label-inner-zip-", ".zip");
        UploadResultVO r;
        try {
          Files.copy(new NonClosingInputStream(outer), innerTmp, StandardCopyOption.REPLACE_EXISTING);
          Charset innerCharset = detectZipCharset(() -> Files.newInputStream(innerTmp));
          try (InputStream in = Files.newInputStream(innerTmp)) {
            // 以内层 zip 名为路径前缀，避免多个内层 zip 内部同名文件互相覆盖
            r = ingestZip(projectId, batchId, uploadId, in, name, name, framesPerEntry, innerCharset);
          }
        } finally {
          Files.deleteIfExists(innerTmp);
        }
        imageCount += r.getImageCount();
        pointCloudCount += r.getPointCloudCount();
        savedCount += r.getSavedCount();
        skippedCount += r.getSkippedCount();
      }
    } catch (IOException e) {
      throw new IllegalStateException("解析 zip 失败：" + e.getMessage(), e);
    }
    writeOperationLog(
        "UPLOAD_ZIP_COLLECTION",
        "project",
        projectId,
        Map.of(
            "fileName", sourceName,
            "uploadCount", uploadCount,
            "savedCount", savedCount));
    UploadResultVO result = new UploadResultVO();
    result.setSavedCount(savedCount);
    result.setImageCount(imageCount);
    result.setPointCloudCount(pointCloudCount);
    result.setSkippedCount(skippedCount);
    result.setUploadId(firstUploadId);
    return result;
  }

  /** 包装 InputStream，使其 close() 不关闭底层流（用于 zip 里套 zip 时复用外层流）。 */
  private static final class NonClosingInputStream extends InputStream {
    private final InputStream delegate;

    NonClosingInputStream(InputStream delegate) {
      this.delegate = delegate;
    }

    @Override
    public int read() throws IOException {
      return delegate.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      return delegate.read(b, off, len);
    }

    @Override
    public void close() {
      // 不关闭底层流，由外层 try-with-resources 统一关闭
    }
  }

  /** 组装元数据归档上下文（收集器创建参数实体）。 */
  private NuscenesArchiveRequest buildMetaArchiveRequest(
      Long projectId, Long batchId, Long uploadId, String sourceName) {
    NuscenesArchiveRequest request = new NuscenesArchiveRequest();
    request.setProjectId(projectId);
    request.setBatchId(batchId);
    request.setUploadId(uploadId);
    request.setSourceName(sourceName);
    request.setUserId(currentUserId());
    return request;
  }

  /**
   * 上传完成后的 nuScenes 元数据自动解析。
   * <p>逻辑：
   * 1. 无元数据或无上传数据ID时直接返回（普通数据包零开销）；
   * 2. 把收集到的元数据归档上传 MinIO 并登记记录——超限等显式业务错误中断上传；
   *    网络类失败自动重试（幂等），重试成功继续自动解析，重试仍失败才降级告警
   *    （此时资产已全部入库，整体回滚会产生 MinIO 孤儿对象且需完整重传，代价不可接受；
   *    可在批次面板点「预标注」手动重试）；
   * 3. 触发解析（点云-相机图关联 + 自动预标注）——失败不影响上传主流程，
   *    归档已落库，可在批次面板通过「预标注」手动重试。</p>
   */
  private void autoParseNuscenesMeta(NuscenesMetaCollector metaCollector, Long uploadId) {
    // 1. 普通数据包直接返回
    if (uploadId == null || !metaCollector.hasMeta()) {
      return;
    }
    // 2. 归档落库：显式业务错误中断上传；网络类失败自动重试（幂等），重试仍失败才降级
    RuntimeException archiveError = null;
    for (int attempt = 1; attempt <= ARCHIVE_RETRY_MAX_ATTEMPTS; attempt++) {
      try {
        metaCollector.finish();
        archiveError = null;
        break;
      } catch (ServiceException e) {
        throw e;
      } catch (RuntimeException e) {
        archiveError = e;
        if (attempt < ARCHIVE_RETRY_MAX_ATTEMPTS) {
          LOGGER.warn("nuScenes 元数据归档上传失败（第 {} 次），退避后自动重试", attempt, e);
          sleepBeforeArchiveRetry(attempt * 2000L, e);
        }
      }
    }
    if (archiveError != null) {
      LOGGER.warn("nuScenes 元数据归档上传失败（已重试 {} 次），跳过自动解析（资产已入库，"
          + "可在批次面板点「预标注」手动重试）", ARCHIVE_RETRY_MAX_ATTEMPTS, archiveError);
      return;
    }
    // 3. 自动解析（失败仅吞掉，不影响上传结果）
    try {
      NuscenesParseDTO parseRequest = new NuscenesParseDTO();
      parseRequest.setUploadId(uploadId);
      NuscenesParseResultVO parseResult = nuscenesImportService.parseUpload(parseRequest);
      // 点云已完成帧切分；相机图按元数据建好的点云-相机图关联归并到点云所在条目
      // （作为参考资产，不进入标注作业），避免图片被当成独立标注条目
      if (parseResult != null && parseResult.getCameraMapCount() != null
          && parseResult.getCameraMapCount() > 0) {
        rebindNuscenesCameraEntries(uploadId);
      }
    } catch (Exception e) {
      // 解析失败：归档状态已由解析侧回写 failed，主流程继续
    }
  }

  /**
   * nuScenes 上传：点云按帧切分为标注条目后，把同帧相机图按 label_nuscenes_asset_map
   * 归并到点云所在条目（作为参考资产，不进入标注作业）；无关联/无点云的相机图兜底归入首个点云条目，
   * 避免孤儿资产。整套上传/重新分割流程末尾调用。
   */
  private void rebindNuscenesCameraEntries(Long uploadId) {
    Long userId = currentUserId();
    // 1. 优先按元数据建好的点云-相机图关联精确归并
    projectMapper.bindCameraImagesToPointCloudEntry(uploadId, userId);
    // 2. 兜底：仍无条目归属的相机图归入首个点云条目（保证可见，仍不进标注作业）
    projectMapper.bindOrphanImagesToFirstPointCloudEntry(uploadId, userId);
    projectMapper.refreshDataEntryAssetCountByUpload(uploadId);
    refreshEntryAssetInfoByUpload(uploadId);
  }

  /** 归档重试前退避；被中断则恢复中断标记并交还原异常（放弃重试）。 */
  private void sleepBeforeArchiveRetry(long millis, RuntimeException cause) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw cause;
    }
  }

  /**
   * 导入已完成分片合并的 MinIO 对象：zip 压缩包解压入库，其他文件按单文件入库。
   * 由「大文件导入」流程在 complete 后调用，实现数据与项目关联。
   * <p>
   * MinIO 为唯一文件源：zip 直接流式解压、逐条目写入资产对象；
   * 单文件在服务端复制到资产对象（copyObject），不再下载回本地。
   * <p>
   * 支持带相对路径的文件名（前端「选择文件夹」上传）：资产地址保留目录层级，
   * 避免不同目录下同名文件互相覆盖；并复用同批次同首层目录的上传数据（来源类型 folder）。
   */
  @Override
  @Transactional
  public UploadResultVO importStoredObject(Long projectId, Long batchId, String fileName, String objectName,
                                          Integer framesPerEntry) {
    String relPath = sanitizeRelativePath(fileName);
    if (relPath.isEmpty()) {
      throw new ServiceException("文件名不合法");
    }
    int fpe = normalizeFramesPerEntry(framesPerEntry);
    Long targetBatchId = resolveBatchId(projectId, batchId);
    boolean isZip = relPath.toLowerCase(Locale.ROOT).endsWith(".zip");
    if (isZip) {
      // zip 压缩包：解压后若第一层含多个条目（且非 nuScenes），则每个顶层条目各成一条上传数据；
      // 否则整体对应一条上传数据（兼容 nuScenes 与普通单目录包）。
      // 先探测条目名编码（Windows 打的 GBK 包 UTF-8 解码会直接崩），全链路按同一编码打开
      Charset nameCharset = null;
      try {
        nameCharset = detectZipCharset(() -> minioService.openObject(objectName));
      } catch (IOException ignored) {
        // 探测自身 IO 失败：按默认编码继续，后续正式扫描会对同一 IO 问题抛「解析 zip 失败」
      }
      ZipTopology topology;
      try (InputStream scan = minioService.openObject(objectName)) {
        topology = scanZipTopology(scan, nameCharset);
      } catch (IOException e) {
        throw new IllegalStateException("解析 zip 失败：" + e.getMessage(), e);
      }
      if (!topology.innerZips.isEmpty()) {
        try (InputStream in = minioService.openObject(objectName)) {
          return ingestZipOfZips(projectId, targetBatchId, in, relPath, topology, fpe, nameCharset);
        } catch (IOException e) {
          throw new IllegalStateException("解析 zip 失败：" + e.getMessage(), e);
        }
      }
      if (topology.nuscenes || topology.segments.size() <= 1) {
        Long zipUploadId =
            createUploadData(projectId, targetBatchId, relPath, null, objectName, UploadSourceType.ZIP);
        try (InputStream in = minioService.openObject(objectName)) {
          return ingestZip(projectId, targetBatchId, zipUploadId, in, relPath, fpe, nameCharset);
        } catch (IOException e) {
          throw new IllegalStateException("解析 zip 失败：" + e.getMessage(), e);
        }
      }
      try (InputStream in = minioService.openObject(objectName)) {
        return ingestZipSplit(projectId, targetBatchId, in, relPath, topology, fpe, nameCharset);
      } catch (IOException e) {
        throw new IllegalStateException("解析 zip 失败：" + e.getMessage(), e);
      }
    }
    String mediaType = mediaTypeOf(relPath);
    if (mediaType == null) {
      throw new IllegalStateException("不支持的文件类型：" + relPath);
    }
    int slash = relPath.indexOf('/');
    // 文件夹上传：同批次同首层目录复用同一条上传数据；独立文件每次生成一条
    Long uploadId;
    if (slash > 0) {
      String folderName = relPath.substring(0, slash);
      Long existed = projectMapper.selectLatestUploadId(projectId, targetBatchId, folderName, UploadSourceType.FOLDER);
      uploadId =
          existed != null
              ? existed
              : createUploadData(projectId, targetBatchId, folderName, null, null, UploadSourceType.FOLDER);
    } else {
      uploadId = createUploadData(projectId, targetBatchId, relPath, null, objectName, UploadSourceType.SINGLE);
    }
    // 资产地址保留相对目录层级，规避同名文件覆盖
    String fileUrl = "/uploads/" + projectId + "/data/" + relPath;
    String objectKey = fileUrl.substring(1);
    minioService.copyObject(objectName, objectKey);
    long size = 0L;
    var info = minioService.statObject(objectKey);
    if (info != null) {
      size = info.size();
    }
    String displayName = slash < 0 ? relPath : relPath.substring(slash + 1);
    AssetVO record =
        createUploadRecord(
            projectId,
            targetBatchId,
            uploadId,
            mediaType,
            displayName,
            size,
            fileUrl,
            MediaType.IMAGE.equals(mediaType) ? fileUrl : null,
            Map.of("source", AssetMetaSource.BIG_FILE_UPLOAD, "objectName", objectName, "relativePath", relPath));
    writeOperationLog(
        "IMPORT_ASSET",
        "project",
        projectId,
        Map.of("fileName", relPath, "mediaType", mediaType));
    UploadResultVO result = new UploadResultVO();
    result.setSavedCount(1);
    result.setImageCount(MediaType.IMAGE.equals(mediaType) ? 1 : 0);
    result.setPointCloudCount(MediaType.POINT_CLOUD.equals(mediaType) ? 1 : 0);
    result.setSkippedCount(0);
    result.setAssetId(record.getId());
    result.setUploadId(uploadId);
    return result;
  }

  private String mediaTypeOf(String fileName) {
    String lower = fileName.toLowerCase(Locale.ROOT);
    // nuScenes RADAR 传感器目录（samples/RADAR_*）的雷达稀疏点不是 LIDAR 点云，
    // 不作为标注资产导入；返回 null 由调用方跳过（不影响普通包，普通包路径不含 RADAR 目录）
    if (isRadarPath(lower)) {
      return null;
    }
    if (lower.endsWith(".png")
        || lower.endsWith(".jpg")
        || lower.endsWith(".jpeg")
        || lower.endsWith(".bmp")
        || lower.endsWith(".webp")
        || lower.endsWith(".gif")) {
      return MediaType.IMAGE;
    }
    if (lower.endsWith(".pcd") || lower.endsWith(".bin")) {
      return MediaType.POINT_CLOUD;
    }
    return null;
  }

  /** 是否 nuScenes RADAR 传感器目录下的文件（含 samples/RADAR_* 等，路径段以 RADAR_ 开头）。 */
  private boolean isRadarPath(String lowerPath) {
    for (String seg : lowerPath.split("/")) {
      if (seg.startsWith("radar_")) {
        return true;
      }
    }
    return false;
  }

  @Override
  @Transactional
  public ProjectVO saveProject(ProjectSaveDTO dto) {
    Long id = dto.getId();
    // 负责人：优先使用下拉框选中的用户ID；兼容旧前端仅传负责人名称文本
    Long ownerId = dto.getOwnerId();
    boolean ownerSpecified = ownerId != null || !isBlank(dto.getOwnerName());
    if (ownerId == null) {
      ownerId = findUserId(dto.getOwnerName(), currentUserId());
    } else if (projectMapper.selectActiveUserId(ownerId) == null) {
      throw new ServiceException("所选负责人不存在或已停用，请重新选择");
    }
    // 新建项目：项目负责人只能从具有「项目负责人」角色的人员中选择
    if (id == null && ownerSpecified) {
      assertUserHasRole(ownerId, LabelRoleConstants.PROJECT_LEADER, "项目负责人");
    }
    // 主队伍：兼容旧字段 teamName（可为空），新增多队伍字段 teamIds（JSON 数组或逗号分隔字符串）
    Long mainTeamId = findOrCreateTeam(dto.getTeamName());
    List<Long> projectTeams = resolveTeams(dto.getTeamIds(), mainTeamId);
    Long teamId = projectTeams.isEmpty() ? null : projectTeams.get(0);
    String status = isBlank(dto.getStatus()) ? ProjectStatus.CREATED : dto.getStatus();
    String dataType = isBlank(dto.getDataType()) ? MediaType.IMAGE : dto.getDataType();
    if (id == null) {
      String projectCode = "PRJ-" + LocalDateTime.now().getYear() + "-" + nextCode("label_project");
      Map<String, Object> p = new HashMap<>();
      p.put("projectCode", projectCode);
      p.put("projectName", dto.getProjectName());
      p.put("description", dto.getDescription());
      p.put("dataType", dataType);
      p.put("ownerId", ownerId);
      p.put("teamId", teamId);
      p.put("status", status);
      p.put("userId", currentUserId());
      projectMapper.insertProject(p);
      long newId = asLong(p.get("id"));
      syncProjectTeams(newId, projectTeams);
      // 新项目初始化默认工作流（原始数据 → 标注 → 审核 → 合格数据），项目之间相互隔离
      workflowService.initDefault(newId);
      return project(newId);
    }
    String existingStatus = projectMapper.selectActiveProjectStatus(id);
    if (existingStatus == null) {
      throw new ServiceException("项目不存在或已归档，无法编辑");
    }
    if (ProjectStatus.COMPLETED.equals(existingStatus) || ProjectStatus.ARCHIVED.equals(existingStatus)) {
      throw new ServiceException("已完成或已归档的项目不可修改");
    }
    // 编辑不更新状态字段，防止通过保存表单把 running/completed 项目重置回 created
    Map<String, Object> p = new HashMap<>();
    p.put("id", id);
    p.put("projectName", dto.getProjectName());
    p.put("description", dto.getDescription());
    p.put("dataType", dataType);
    p.put("ownerId", ownerId);
    p.put("teamId", teamId);
    p.put("userId", currentUserId());
    projectMapper.updateProject(p);
    syncProjectTeams(id, projectTeams);
    return project(id);
  }

  /** 合并主队伍与请求中的参与队伍ID（去重、过滤空值）。 */
  private List<Long> resolveTeams(List<Long> teamIds, Long mainTeamId) {
    List<Long> merged = new ArrayList<>();
    if (mainTeamId != null) {
      merged.add(mainTeamId);
    }
    if (teamIds != null) {
      for (Long t : teamIds) {
        if (t != null && !merged.contains(t)) {
          merged.add(t);
        }
      }
    }
    return merged;
  }

  private boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  /**
   * 项目成员：按绑定的团队展开（主团队 label_project.team_id ∪ label_project_team 参与团队），
   * 返回 label_team_member 中全部成员的团队归属与队内角色。同一用户加入多支团队会出现多行。
   */
  @Override
  public List<ProjectMemberVO> projectMembers(Long projectId) {
    return projectMapper.selectProjectMembers(projectId);
  }

  // ---------------- 标签体系 ----------------

  @Override
  public PageResult<ClassSetVO> classSets(Long projectId) {
    return PageResult.of(projectMapper.selectClassSets(projectId));
  }

  @Override
  public List<ClassVO> classes(Long classSetId) {
    List<ClassVO> list = projectMapper.selectClasses(classSetId);
    // 装配属性：列表接口直接带出，前端表格 / 编辑弹窗无需再单独查询
    list.forEach(this::fillClassAttributes);
    return list;
  }

  @Override
  @Transactional
  public ClassSetVO createClassSet(Long projectId, ClassSetDTO dto) {
    Map<String, Object> p = new HashMap<>();
    p.put("projectId", projectId);
    p.put("name", dto.getName());
    p.put("version", dto.getVersion());
    p.put("userId", currentUserId());
    projectMapper.insertClassSet(p);
    return projectMapper.selectClassSetById(asLong(p.get("id")));
  }

  @Override
  public ClassSetVO updateClassSet(Long id, ClassSetDTO dto) {
    projectMapper.updateClassSet(id, dto.getName(), dto.getVersion(), currentUserId());
    return projectMapper.selectClassSetById(id);
  }

  @Override
  public void deleteClassSet(Long id) {
    projectMapper.deleteClassSet(id, currentUserId());
  }

  @Override
  @Transactional
  public ClassVO createClass(Long classSetId, ClassDTO dto) {
    Map<String, Object> p = new HashMap<>();
    p.put("classSetId", classSetId);
    p.put("labelName", dto.getLabelName());
    p.put("color", dto.getColor());
    p.put("toolType", dto.getToolType());
    p.put("defaultSizeL", dto.getDefaultSizeL());
    p.put("defaultSizeW", dto.getDefaultSizeW());
    p.put("defaultSizeH", dto.getDefaultSizeH());
    // 刚体标识：Boolean -> 0/1（库列为 tinyint）；null 交由 COALESCE 保留原值，创建时默认 0（非刚体）
    p.put("isRigid", rigidFlag(dto.getIsRigid(), 0));
    p.put("minInsidePoints", dto.getMinInsidePoints());
    // 关键点模板：仅在配置了时写入，避免把 NULL 序列化成 "null" 污染 json 列
    if (dto.getKeypointTemplate() != null) {
      p.put("keypointTemplateJson", toJson(dto.getKeypointTemplate()));
    }
    p.put("sortOrder", dto.getSortOrder() == null ? 0 : dto.getSortOrder());
    p.put("userId", currentUserId());
    projectMapper.insertClass(p);
    return withClassAttributes(projectMapper.selectClassById(asLong(p.get("id"))));
  }

  @Override
  public ClassVO updateClass(Long id, ClassDTO dto) {
    // 未传模板时传 null，COALESCE 保留库中原值，避免编辑其它字段时把模板清空
    String templateJson = dto.getKeypointTemplate() == null ? null : toJson(dto.getKeypointTemplate());
    projectMapper.updateClass(id, dto.getLabelName(), dto.getColor(), dto.getToolType(),
        dto.getSortOrder() == null ? 0 : dto.getSortOrder(),
        dto.getDefaultSizeL(), dto.getDefaultSizeW(), dto.getDefaultSizeH(),
        // 刚体标识：null 保留库中原值（编辑其它字段不清空）；true/false 落 1/0
        rigidFlag(dto.getIsRigid(), null),
        dto.getMinInsidePoints(), templateJson, currentUserId());
    return withClassAttributes(projectMapper.selectClassById(id));
  }

  @Override
  public void deleteClass(Long id) {
    projectMapper.deleteClass(id, currentUserId());
  }

  /**
   * 刚体标识 Boolean -> 0/1：库列 is_rigid 为 tinyint。
   * value 为 null 时返回 fallback（创建时给 0=非刚体；更新时给 null 走 COALESCE 保留原值）。
   */
  private Integer rigidFlag(Boolean value, Integer fallback) {
    if (value == null) {
      return fallback;
    }
    return value ? 1 : 0;
  }

  @Override
  public ClassVO updateClassAttributes(Long id, ClassAttributeDTO dto) {
    insertClassAttribute(id, dto);
    return withClassAttributes(projectMapper.selectClassById(id));
  }

  /**
   * 标签属性列表：attr_options 是 JSON 列，这里把读取到的 JSON 文本反解析回原始值列表，
   * 让「编辑标签」弹窗能原样回显已配置的属性。无属性时返回空数组（前端不会拿到 null）。
   */
  @Override
  public List<ClassAttributeVO> classAttributes(Long id) {
    List<ClassAttributeVO> list = projectMapper.selectClassAttributes(id);
    if (list == null || list.isEmpty()) {
      return List.of();
    }
    list.forEach((attr) -> {
      Object options = parseJson(attr.getAttrOptionsJson());
      attr.setAttrOptions(options instanceof List<?> items ? new ArrayList<>(items) : List.of());
      attr.setAttrOptionsJson(null);
    });
    return list;
  }

  /**
   * 整份覆盖保存标签属性：先软删除该标签原有属性，再按提交顺序重新写入。
   * 此前前端是「逐条 upsert」，删掉的属性永远留在库里、保存后也回显不出来（表现为属性清空 / 存不上）。
   */
  @Override
  @Transactional
  public ClassVO replaceClassAttributes(Long id, List<ClassAttributeDTO> list) {
    projectMapper.deleteClassAttributesByClass(id, currentUserId());
    if (list != null) {
      for (ClassAttributeDTO dto : list) {
        if (dto == null || isBlank(dto.getAttrName())) {
          continue;
        }
        insertClassAttribute(id, dto);
      }
    }
    return withClassAttributes(projectMapper.selectClassById(id));
  }

  /** 单条属性写入：对按属性名幂等的 upsert 语句收口（非空 / 默认值在此统一处理） */
  private void insertClassAttribute(Long id, ClassAttributeDTO dto) {
    projectMapper.insertClassAttribute(
        id,
        dto.getAttrName(),
        dto.getAttrType(),
        // 选项列表统一序列化为 JSON 文本；空列表也落 []，避免 json 列出现 "null"
        toJson(dto.getAttrOptions() == null ? List.of() : dto.getAttrOptions()),
        dto.getRequired() == null ? 0 : dto.getRequired(),
        dto.getMaxLength(),
        currentUserId());
  }

  /** 装配单个标签的属性（回显用） */
  private ClassVO withClassAttributes(ClassVO vo) {
    fillClassAttributes(vo);
    return vo;
  }

  private void fillClassAttributes(ClassVO vo) {
    if (vo != null) {
      vo.setAttributes(classAttributes(vo.getId()));
    }
  }

  // ---------------- 全局属性 ----------------

  /**
   * 全局属性配置列表：attr_options 是 JSON 列，反解析回原始值列表供前端回显。
   * 无配置时返回空数组（前端不会拿到 null）。
   */
  @Override
  public List<ClassAttributeVO> globalAttributes(Long projectId) {
    List<ClassAttributeVO> list = projectMapper.selectGlobalAttributes(projectId);
    if (list == null || list.isEmpty()) {
      return List.of();
    }
    list.forEach((attr) -> {
      Object options = parseJson(attr.getAttrOptionsJson());
      attr.setAttrOptions(options instanceof List<?> items ? new ArrayList<>(items) : List.of());
      attr.setAttrOptionsJson(null);
    });
    return list;
  }

  /** 整份覆盖保存全局属性：先软删除全部，再按提交顺序写入（同标签属性的整份覆盖语义）。 */
  @Override
  @Transactional
  public List<ClassAttributeVO> replaceGlobalAttributes(Long projectId, List<ClassAttributeDTO> list) {
    projectMapper.deleteAllGlobalAttributes(projectId, currentUserId());
    if (list != null) {
      for (ClassAttributeDTO dto : list) {
        if (dto == null || isBlank(dto.getAttrName())) {
          continue;
        }
        projectMapper.insertGlobalAttribute(
            projectId,
            dto.getAttrName(),
            dto.getAttrType(),
            toJson(dto.getAttrOptions() == null ? List.of() : dto.getAttrOptions()),
            dto.getRequired() == null ? 0 : dto.getRequired(),
            dto.getMaxLength(),
            currentUserId());
      }
    }
    return globalAttributes(projectId);
  }

  // ---------------- 资产数据 ----------------

  @Override
  public PageResult<AssetBatchVO> assetBatches(Long projectId) {
    return PageResult.of(projectMapper.selectAssetBatches(projectId));
  }

  @Override
  public AssetBatchVO assetBatch(Long id) {
    AssetBatchVO batch = projectMapper.selectAssetBatchById(id);
    if (batch == null) {
      throw new ServiceException("数据批次不存在");
    }
    return batch;
  }

  /** 新建数据批次：批次号自动生成，名称由用户指定，之后可重命名。 */
  @Override
  @Transactional
  public AssetBatchVO createBatch(Long projectId, BatchSaveDTO dto) {
    if (projectMapper.selectProjectStatus(projectId) == null) {
      throw new ServiceException("项目不存在");
    }
    Map<String, Object> p = new HashMap<>();
    p.put("projectId", projectId);
    p.put("batchNo", "BATCH-" + nextCode("label_asset_batch"));
    p.put("batchName", dto.getBatchName().trim());
    p.put("remark", dto.getRemark());
    p.put("sourceType", UploadSourceType.MANUAL);
    p.put("userId", currentUserId());
    projectMapper.insertAssetBatch(p);
    Long id = asLong(p.get("id"));
    writeOperationLog(
        "CREATE_BATCH", "batch", id, Map.of("projectId", projectId, "batchName", dto.getBatchName()));
    return projectMapper.selectAssetBatchById(id);
  }

  /** 重命名数据批次 / 修改备注。 */
  @Override
  @Transactional
  public AssetBatchVO updateBatch(Long id, BatchSaveDTO dto) {
    assetBatch(id);
    projectMapper.updateAssetBatch(id, dto.getBatchName().trim(), dto.getRemark(), currentUserId());
    writeOperationLog("UPDATE_BATCH", "batch", id, Map.of("batchName", dto.getBatchName()));
    return projectMapper.selectAssetBatchById(id);
  }

  /** 删除数据批次：批次内仍有数据时必须显式 force 才级联软删除。 */
  @Override
  @Transactional
  public void deleteBatch(Long id, boolean force) {
    AssetBatchVO batch = assetBatch(id);
    long assetCount = projectMapper.countAssetByBatch(id);
    long sequenceCount = projectMapper.countSequenceByBatch(id);
    if (!force && (assetCount > 0 || sequenceCount > 0)) {
      throw new ServiceException(
          "批次内还有 " + assetCount + " 条数据资产、" + sequenceCount + " 个序列，请确认后连同数据一起删除");
    }
    if (force) {
      projectMapper.deleteAssetsByBatch(id, currentUserId());
      projectMapper.deleteSequencesByBatch(id, currentUserId());
    }
    projectMapper.deleteDataEntriesByBatch(id, currentUserId());
    projectMapper.deleteUploadsByBatch(id, currentUserId());
    projectMapper.deleteAssetBatch(id, currentUserId());
    writeOperationLog(
        "DELETE_BATCH",
        "batch",
        id,
        Map.of(
            "batchNo", stringValue(batch.getBatchNo()),
            "assetCount", assetCount,
            "sequenceCount", sequenceCount));
  }

  // ---------------- 上传数据 ----------------

  /** 批次下的上传数据列表：每次上传/导入对应一条，其下挂载数据资产。 */
  @Override
  public PageResult<UploadDataVO> batchUploads(Long batchId) {
    return PageResult.of(projectMapper.selectUploadsByBatch(batchId));
  }

  @Override
  public UploadDataVO uploadData(Long id) {
    UploadDataVO upload = projectMapper.selectUploadById(id);
    if (upload == null) {
      throw new ServiceException("上传数据不存在");
    }
    return upload;
  }

  /** 删除上传数据：其下仍有数据资产时必须显式 force 才级联软删除。 */
  @Override
  @Transactional
  public void deleteUpload(Long id, boolean force) {
    UploadDataVO upload = uploadData(id);
    long assetCount = projectMapper.countAssetByUpload(id);
    if (!force && assetCount > 0) {
      throw new ServiceException("上传数据下还有 " + assetCount + " 条数据资产，请确认后连同数据一起删除");
    }
    if (force) {
      projectMapper.deleteAssetsByUpload(id, currentUserId());
    }
    projectMapper.deleteDataEntriesByUpload(id, currentUserId());
    projectMapper.deleteUpload(id, currentUserId());
    writeOperationLog(
        "DELETE_UPLOAD",
        "upload",
        id,
        Map.of("uploadNo", stringValue(upload.getUploadNo()), "assetCount", assetCount));
  }

  // ---------------- 数据条目（上传数据下的数据集合包） ----------------

  /** 上传数据下的数据条目列表（一个条目 = 一个数据集合包）。 */
  @Override
  public PageResult<DataEntryVO> uploadEntries(Long uploadId) {
    uploadData(uploadId);
    return PageResult.of(projectMapper.selectEntriesByUpload(uploadId));
  }

  /**
   * 分割数据：一次上传的数据是一个数据集合包，上传结束后可选择不分割（整包即 1 个条目），
   * 或按设置平均分割成多个条目；已下发给标注任务的数据不允许重新分割。
   */
  @Override
  @Transactional
  public List<DataEntryVO> splitUploadEntries(Long uploadId, EntrySplitDTO dto) {
    UploadDataVO upload = uploadData(uploadId);
    // 点云上传按点云帧切分；无点云（纯图片）时退化为全部资产切分
    List<Long> pcAssetIds = projectMapper.selectPointCloudAssetIdsByUpload(uploadId);
    List<Long> assetIds = pcAssetIds.isEmpty() ? projectMapper.selectAssetIdsByUpload(uploadId) : pcAssetIds;
    if (assetIds.isEmpty()) {
      throw new ServiceException("该上传数据下暂无数据，无法分割");
    }
    if (projectMapper.countAssignedAssetByUpload(uploadId) > 0) {
      throw new ServiceException("该上传数据已下发给标注任务，不能重新分割");
    }
    String splitMode = resolveSplitMode(dto);
    List<List<Long>> groups = splitGroups(assetIds, splitMode, dto);
    // userId 仅用于 updated_by 审计字段，允许为 null，避免 Long 自动拆箱触发 NPE
    Long userId = currentUserId();
    projectMapper.deleteDataEntriesByUpload(uploadId, userId);
    projectMapper.clearAssetEntryByUpload(uploadId, userId);
    int seqNo = 1;
    for (List<Long> group : groups) {
      Long entryId = createDataEntry(upload, seqNo, groups.size(), splitMode);
      projectMapper.bindAssetsToEntry(entryId, group, userId);
      seqNo++;
    }
    projectMapper.refreshDataEntryAssetCountByUpload(uploadId);
    // 新条目绑定的资产信息（media_type/parse_status/first_asset_id）落冗余列
    refreshEntryAssetInfoByUpload(uploadId);
    // nuScenes 上传：重新切分后把相机图按关联归并到点云条目（作为参考资产）
    rebindNuscenesCameraEntries(uploadId);
    writeOperationLog(
        "SPLIT_UPLOAD_ENTRIES",
        "upload",
        uploadId,
        Map.of("splitMode", splitMode, "entryCount", groups.size(), "assetCount", assetIds.size()));
    return projectMapper.selectEntriesByUpload(uploadId);
  }

  /** 规范化分割方式：为空按「不分割」处理。 */
  private String resolveSplitMode(EntrySplitDTO dto) {
    String mode = dto == null || dto.getSplitMode() == null ? "" : dto.getSplitMode().trim().toLowerCase(Locale.ROOT);
    if (mode.isEmpty() || "whole".equals(mode)) {
      return "whole";
    }
    if ("count".equals(mode)) {
      if (dto.getSize() == null || dto.getSize() < 1) {
        throw new ServiceException("按数量分割时，每份数据量必须大于 0");
      }
      return "count";
    }
    if ("parts".equals(mode)) {
      if (dto.getParts() == null || dto.getParts() < 1) {
        throw new ServiceException("按份数分割时，分割份数必须大于 0");
      }
      return "parts";
    }
    throw new ServiceException("不支持的分割方式，请选择不分割、按数量或按份数");
  }

  /** 计算分割分组：不分割=整包一组；按数量=每份 size 条；按份数=均分（余数优先分给前面的条目）。 */
  private List<List<Long>> splitGroups(List<Long> assetIds, String splitMode, EntrySplitDTO dto) {
    int total = assetIds.size();
    List<List<Long>> groups = new ArrayList<>();
    if ("whole".equals(splitMode)) {
      groups.add(new ArrayList<>(assetIds));
      return groups;
    }
    if ("count".equals(splitMode)) {
      int size = dto.getSize();
      for (int start = 0; start < total; start += size) {
        groups.add(new ArrayList<>(assetIds.subList(start, Math.min(total, start + size))));
      }
      return groups;
    }
    int parts = dto.getParts();
    if (parts > total) {
      throw new ServiceException("分割份数不能大于该上传的数据条数（" + total + "）");
    }
    int base = total / parts;
    int remainder = total % parts;
    int cursor = 0;
    for (int i = 0; i < parts; i++) {
      int take = base + (i < remainder ? 1 : 0);
      groups.add(new ArrayList<>(assetIds.subList(cursor, cursor + take)));
      cursor += take;
    }
    return groups;
  }

  /** 新建数据条目（数据集合包），返回主键。 */
  private Long createDataEntry(UploadDataVO upload, int seqNo, int groupCount, String splitMode) {
    String baseName =
        isBlank(upload.getFileName())
            ? "上传数据 " + stringValue(upload.getUploadNo())
            : upload.getFileName();
    Map<String, Object> p = new HashMap<>();
    p.put("projectId", upload.getProjectId());
    p.put("batchId", upload.getBatchId());
    p.put("uploadId", upload.getId());
    p.put("entryName", groupCount > 1 ? baseName + " #" + seqNo : baseName);
    p.put("sourceFile", upload == null ? null : upload.getFileName());
    p.put("seqNo", seqNo);
    p.put("splitMode", splitMode);
    p.put("taskId", upload.getBatchId() == null ? null : projectMapper.selectTaskIdByBatch(upload.getBatchId()));
    p.put("userId", currentUserId());
    insertEntryWithRetry(p);
    return asLong(p.get("id"));
  }

  /**
   * 插入数据条目：条目号按项目内独立编号（ENTRY-&lt;项目内序号&gt;，不同项目互不影响）。
   *
   * <p>取号是「查项目内最大序号 + 1」，查询为 FOR UPDATE 锁定读（见 selectMaxEntryNoSeq），
   * 同项目并发取号被串行化；多实例部署等残余竞态撞上 (project_id, entry_no)
   * 唯一索引时换号重试（带退避，最多 5 次），避免整包数据因编号冲突导入失败。
   */
  private void insertEntryWithRetry(Map<String, Object> p) {
    Long projectId = asLong(p.get("projectId"));
    for (int attempt = 1; ; attempt++) {
      p.put("entryNo", "ENTRY-" + nextEntryNoSeq(projectId));
      try {
        projectMapper.insertDataEntry(p);
        return;
      } catch (DuplicateKeyException ex) {
        if (attempt >= 5) {
          throw ex;
        }
        try {
          Thread.sleep(50L * attempt);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw ex;
        }
      }
    }
  }

  /**
   * 上传/导入入库后，把数据归入其上传数据的默认条目：默认不分割，即整包数据为一个条目；
   * 若该上传已执行过分割，则新入库数据追加到序号最大的条目上。
   */
  private void attachAssetsToDefaultEntry(Long uploadId, List<Long> assetIds) {
    if (uploadId == null || assetIds == null || assetIds.isEmpty()) {
      return;
    }
    List<Long> validIds = assetIds.stream().filter(Objects::nonNull).toList();
    if (validIds.isEmpty()) {
      return;
    }
    Long entryId = projectMapper.selectDefaultEntryIdByUpload(uploadId);
    if (entryId == null) {
      entryId = projectMapper.selectLastEntryIdByUpload(uploadId);
    }
    if (entryId == null) {
      UploadDataVO upload = projectMapper.selectUploadById(uploadId);
      if (upload == null) {
        return;
      }
      entryId = createDataEntry(upload, 1, 1, "whole");
    }
    projectMapper.bindAssetsToEntry(entryId, validIds, currentUserId());
    projectMapper.refreshDataEntryAssetCountByUpload(uploadId);
    refreshEntryAssetInfoByUpload(uploadId);
  }

  /** 条目号发号 Redis 计数器 key 前缀：按项目独立计数（label:entry_no:seq:{projectId}），多实例共享。 */
  private static final String ENTRY_NO_SEQ_KEY = "label:entry_no:seq:";

  /** 发号计数器 TTL：兜底过期；key 丢失后会以库中真实 max 自动重建，不会发出重复号。 */
  private static final Duration ENTRY_NO_SEQ_TTL = Duration.ofDays(30);

  /**
   * 生成项目内的条目号序号（不同项目互不影响，各自从 1 开始）。
   *
   * <p>多实例部署下发号必须跨进程互斥，进程内锁无效，改用 Redis 原子 INCR：
   * key 不存在时（首次发号 / key 过期 / Redis 数据丢失）以库中真实 max 为起点初始化——
   * 初始化查询为 FOR UPDATE 锁定读（等待并发事务提交后取最新值），
   * SET NX 语义保证多实例同时初始化时只有一个生效、不会覆盖更大的当前值；
   * 之后的发号全部走 Redis 原子自增，各实例天然互斥。
   * 序号在项目内不复用（导入回滚只造成跳号，不影响唯一性，含已软删除的条目）。</p>
   */
  private long nextEntryNoSeq(Long projectId) {
    String key = ENTRY_NO_SEQ_KEY + projectId;
    if (!Boolean.TRUE.equals(RedisUtils.hasKey(key))) {
      Long max = projectMapper.selectMaxEntryNoSeq(projectId);
      RedisUtils.setObjectIfAbsent(key, max == null ? 0L : max, ENTRY_NO_SEQ_TTL);
    }
    return RedisUtils.incrAtomicValue(key);
  }

  /** 每批帧数防护上限，避免异常大值导致条目数爆炸。 */
  private static final int MAX_FRAMES_PER_ENTRY_LIMIT = 100_000;
  /** 单条目默认承载的帧数（点云/图片场景下 1 个 asset = 1 帧）。可在导入时配置，缺省 60。 */
  private static final int DEFAULT_FRAMES_PER_ENTRY = 60;

  /** 归一化「每批帧数」：null 或 <=0 回退默认 60，过大截断到防护上限。 */
  private static int normalizeFramesPerEntry(Integer value) {
    if (value == null || value <= 0) {
      return DEFAULT_FRAMES_PER_ENTRY;
    }
    return Math.min(value, MAX_FRAMES_PER_ENTRY_LIMIT);
  }

  /**
   * 入库后把资产归入标注条目：点云上传与纯图片上传均按 framesPerEntry 自动拆分为多个标注条目。
   * <p>图片导入也按帧切分（默认 60 帧/条，可在导入时配置）；点云上传此前已按帧切分，现改为统一可配置。
   * 资产已下发给标注任务的（复用既有分组）仍由 {@link #autoSplitEntriesByFrameLimit} 内部退化为整包条目。</p>
   */
  private void attachOrAutoSplitEntry(Long uploadId, List<Long> assetIds, int framesPerEntry) {
    if (uploadId == null || assetIds == null || assetIds.isEmpty()) {
      return;
    }
    autoSplitEntriesByFrameLimit(uploadId, framesPerEntry);
  }

  /**
   * 点云资产增量归入「同批次最近一个未满（< MAX_FRAMES_PER_ENTRY）的点云标注条目」，满则新建。
   * 采用批次维度而非单个上传维度：前端逐文件上传时每个文件各自是一个上传数据，但点云应按批次聚合成
   * 「最多 60 个一条目」，否则会出现「一个文件一个条目」。跨 upload 归并不影响标注可见性（条目带 task_id）。
   */
  private void appendPointCloudAssetToEntry(Long batchId, Long uploadId, Long assetId) {
    Long entryId = projectMapper.selectLastOpenPointCloudEntryByBatch(batchId, DEFAULT_FRAMES_PER_ENTRY);
    Long ownerUploadId = uploadId;
    if (entryId == null) {
      UploadDataVO upload = projectMapper.selectUploadById(uploadId);
      int seqNo = projectMapper.selectMaxEntrySeqNoByBatch(batchId) + 1;
      entryId = createDataEntry(upload, seqNo, seqNo, "count");
    } else {
      DataEntryVO entry = projectMapper.selectDataEntryById(entryId);
      if (entry != null && entry.getUploadId() != null) {
        ownerUploadId = entry.getUploadId();
      }
    }
    projectMapper.bindAssetsToEntry(entryId, List.of(assetId), currentUserId());
    projectMapper.refreshDataEntryAssetCountByUpload(ownerUploadId);
    refreshEntryAssetInfoByUpload(ownerUploadId);
  }

  /** 自动按帧数上限把一次上传的数据切分为多个条目（复用 count 切分逻辑，每组最多 maxFrames 帧）。 */
  private void autoSplitEntriesByFrameLimit(Long uploadId, int maxFrames) {
    if (uploadId == null) {
      return;
    }
    // 点云（标注资源）作为「帧」切分标注条目；相机图作为参考资产暂不归入条目，
    // 待元数据解析建好点云-相机图关联后，由 rebindNuscenesCameraEntries 归并到点云所在条目。
    List<Long> pcAssetIds = projectMapper.selectPointCloudAssetIdsByUpload(uploadId);
    List<Long> assetIds = pcAssetIds.isEmpty() ? projectMapper.selectAssetIdsByUpload(uploadId) : pcAssetIds;
    if (assetIds.isEmpty()) {
      return;
    }
    // 资产已下发给标注任务则不再拆分，保持原有条目结构
    if (projectMapper.countAssignedAssetByUpload(uploadId) > 0) {
      attachAssetsToDefaultEntry(uploadId, assetIds);
      return;
    }
    EntrySplitDTO splitDto = new EntrySplitDTO();
    splitDto.setSplitMode("count");
    splitDto.setSize(maxFrames);
    List<List<Long>> groups = splitGroups(assetIds, "count", splitDto);
    UploadDataVO upload = projectMapper.selectUploadById(uploadId);
    // 后台导入线程无登录上下文，currentUserId() 可能为 null；此处仅用于 updated_by 审计字段，
    // 允许为空，避免 Long 自动拆箱触发 NPE。
    Long userId = currentUserId();
    // 清掉 ingest 阶段可能已建的整包默认条目，再按帧数上限重建多个条目
    projectMapper.deleteDataEntriesByUpload(uploadId, userId);
    projectMapper.clearAssetEntryByUpload(uploadId, userId);
    int seqNo = 1;
    for (List<Long> group : groups) {
      Long entryId = createDataEntry(upload, seqNo, groups.size(), "count");
      projectMapper.bindAssetsToEntry(entryId, group, userId);
      seqNo++;
    }
    projectMapper.refreshDataEntryAssetCountByUpload(uploadId);
    refreshEntryAssetInfoByUpload(uploadId);
  }

  /** 刷新该上传数据下全部条目的数据资产信息冗余列（绑定/分割流程末尾调用）。 */
  private void refreshEntryAssetInfoByUpload(Long uploadId) {
    if (uploadId == null) {
      return;
    }
    projectMapper.refreshEntryAssetInfoByUpload(uploadId,
        MediaType.MIXED, AssetParseStatus.FAILED, AssetParseStatus.SUCCESS);
  }

  /** 刷新资产所属条目的数据资产信息冗余列（单资产状态变更后调用）。 */
  private void refreshEntryAssetInfoByAssetId(Long assetId) {
    if (assetId == null) {
      return;
    }
    projectMapper.refreshEntryAssetInfoByAssetId(assetId,
        MediaType.MIXED, AssetParseStatus.FAILED, AssetParseStatus.SUCCESS);
  }

  /**
   * 新增一条上传数据记录（上传/导入时调用），返回其主键。
   *
   * <p>编号规则 = 「UPLOAD-自增id」：先以全局唯一占位值插入拿到自增 id，再回填正式编号。
   * 旧的「查最大 id + 1」在并发上传时必然撞 uk_upload_no —— 未提交事务的行互相不可见（MVCC），
   * 各并发请求在对方提交前读到同一个 max，JVM 取号锁救不了跨事务的可见性；
   * 用行自身 id 生成编号则由主键天然保证唯一，无需任何锁。
   */
  private Long createUploadData(
      Long projectId, Long batchId, String fileName, Long fileSize, String objectName, String sourceType) {
    Map<String, Object> u = new HashMap<>();
    u.put("projectId", projectId);
    u.put("batchId", batchId);
    u.put("uploadNo", "UPLOAD-TMP-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
    u.put("fileName", fileName);
    u.put("fileSize", fileSize == null ? 0L : fileSize);
    u.put("objectName", objectName);
    u.put("sourceType", sourceType);
    u.put("status", UploadDataStatus.COMPLETED);
    u.put("userId", currentUserId());
    projectMapper.insertUpload(u);
    long id = asLong(u.get("id"));
    projectMapper.updateUploadNo(id);
    return id;
  }

  @Override
  public PageResult<AssetVO> assets(
      Long projectId, Long batchId, String mediaType, String parseStatus, int pageNum, int pageSize) {
    return assets(projectId, batchId, null, null, mediaType, parseStatus, pageNum, pageSize);
  }

  @Override
  public PageResult<AssetVO> assets(
      Long projectId,
      Long batchId,
      Long uploadId,
      Long entryId,
      String mediaType,
      String parseStatus,
      int pageNum,
      int pageSize) {
    int safePage = Math.max(1, pageNum);
    int safeSize = Math.max(0, pageSize);
    long total = projectMapper.countAssets(projectId, batchId, uploadId, entryId, mediaType, parseStatus);
    List<AssetVO> list =
        safeSize > 0
            ? projectMapper.selectAssets(
                projectId, batchId, uploadId, entryId, mediaType, parseStatus, safeSize,
                (safePage - 1) * safeSize)
            : projectMapper.selectAssets(projectId, batchId, uploadId, entryId, mediaType, parseStatus, 0, 0);
    return new PageResult<>(total, list);
  }

  @Override
  public AssetVO asset(Long id) {
    AssetVO asset = projectMapper.selectAssetById(id);
    if (asset == null) {
      throw new ServiceException("资产不存在");
    }
    return asset;
  }

  @Override
  public AssetVO assetPreview(Long id) {
    AssetVO asset = asset(id);
    String thumb = asset.getThumbnailUrl();
    String preview = (thumb == null || thumb.isBlank()) ? asset.getFileUrl() : thumb;
    asset.setPreviewUrl(preview);
    return asset;
  }

  @Override
  public AssetVO retryAsset(Long id) {
    projectMapper.retryAsset(id, currentUserId());
    // parse_status 由 failed 恢复，重算所属条目的聚合解析状态冗余列
    refreshEntryAssetInfoByAssetId(id);
    writeOperationLog("RETRY_ASSET_PARSE", "asset", id, Map.of("assetId", id));
    return asset(id);
  }

  @Override
  @Transactional
  public AssetVO createUpload(ProjectAssetUploadDTO dto) {
    Long projectId = dto.getProjectId() == null ? 1L : dto.getProjectId();
    String mediaType = isBlank(dto.getMediaType()) ? MediaType.IMAGE : dto.getMediaType();
    String fileName = sanitizeAssetFileName(dto.getFileName());
    long fileSize = parseFileSize(dto.getFileSize() == null ? 0 : dto.getFileSize());
    Long batchId = resolveBatchId(projectId, null);
    Long uploadId = createUploadData(projectId, batchId, fileName, fileSize, null, UploadSourceType.MANUAL);
    return createUploadRecord(
        projectId,
        batchId,
        uploadId,
        mediaType,
        fileName,
        fileSize,
        "/assets/" + fileName,
        MediaType.IMAGE.equals(mediaType) ? "/assets/thumb/" + fileName : null,
        Map.of("source", AssetMetaSource.MANUAL));
  }

  @Override
  @Transactional
  public AssetVO createUpload(Long projectId, String mediaType, MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new ServiceException("上传文件不能为空");
    }
    String originalFileName = sanitizeAssetFileName(file.getOriginalFilename());
    String storedFileName = System.currentTimeMillis() + "-" + originalFileName;
    String fileUrl = "/uploads/" + projectId + "/" + mediaType + "/" + storedFileName;
    try (InputStream inputStream = file.getInputStream()) {
      minioService.putObject(
          fileUrl.substring(1), inputStream, file.getSize(), minioService.contentTypeFor(originalFileName));
    } catch (IOException e) {
      throw new IllegalStateException("上传文件失败：" + e.getMessage(), e);
    }
    Long batchId = resolveBatchId(projectId, null);
    Long uploadId = createUploadData(projectId, batchId, originalFileName, file.getSize(), fileUrl.substring(1), UploadSourceType.SINGLE);
    return createUploadRecord(
        projectId,
        batchId,
        uploadId,
        mediaType,
        originalFileName,
        file.getSize(),
        fileUrl,
        MediaType.IMAGE.equals(mediaType) ? fileUrl : null,
        Map.of("source", AssetMetaSource.MINIO_UPLOAD, "objectKey", fileUrl.substring(1)));
  }

  private AssetVO createUploadRecord(
      Long projectId,
      Long batchId,
      Long uploadId,
      String mediaType,
      String fileName,
      long fileSize,
      String fileUrl,
      String thumbnailUrl,
      Map<String, ?> meta) {
    Map<String, Object> a = new HashMap<>();
    a.put("projectId", projectId);
    a.put("batchId", batchId);
    a.put("uploadId", uploadId);
    a.put("mediaType", mediaType);
    a.put("fileName", fileName);
    a.put("fileUrl", fileUrl);
    a.put("thumbnailUrl", thumbnailUrl);
    a.put("fileSize", fileSize);
    int[] dim = readImageSize(fileUrl);
    a.put("width", dim == null ? 0 : dim[0]);
    a.put("height", dim == null ? 0 : dim[1]);
    a.put("metaJson", toJson(meta));
    a.put("userId", currentUserId());
    projectMapper.insertAsset(a);
    long id = asLong(a.get("id"));
    projectMapper.bumpAssetBatch(batchId);
    if (uploadId != null) {
      projectMapper.refreshUploadAssetCount(uploadId);
      // 点云（.pcd/.bin）增量归入最近一个未满（< MAX_FRAMES_PER_ENTRY）的点云标注条目，满则新建，
      // 保证「最多 60 个数据一个条目」；纯图片退化为整包 1 个条目。
      if (MediaType.POINT_CLOUD.equals(mediaType)) {
        appendPointCloudAssetToEntry(batchId, uploadId, id);
      } else {
        attachAssetsToDefaultEntry(uploadId, List.of(id));
      }
    }
    writeOperationLog("UPLOAD_ASSET", "asset", id, Map.of("fileName", fileName, "mediaType", mediaType));
    AssetVO vo = projectMapper.selectAssetById(id);
    if (vo == null) {
      throw new ServiceException("资产写入失败");
    }
    return vo;
  }

  // ---------------- 团队信息填充 ----------------

  private void fillProjectTeams(List<ProjectVO> rows) {
    if (rows == null || rows.isEmpty()) {
      return;
    }
    List<Long> ids = rows.stream().map(ProjectVO::getId).filter(Objects::nonNull).toList();
    if (ids.isEmpty()) {
      return;
    }
    Map<Long, ProjectVO> byId = new HashMap<>();
    for (ProjectVO r : rows) {
      byId.put(r.getId(), r);
    }
    for (Map<String, Object> rel : labelCommonMapper.selectProjectTeams(ids)) {
      Long pid = asLong(rel.get("projectId"));
      ProjectVO vo = byId.get(pid);
      if (vo == null) {
        continue;
      }
      Long tid = asLong(rel.get("teamId"));
      if (tid == null) {
        continue;
      }
      if (vo.getTeamIds() == null) {
        vo.setTeamIds(new ArrayList<>());
      }
      if (vo.getTeamNames() == null) {
        vo.setTeamNames(new ArrayList<>());
      }
      vo.getTeamIds().add(tid);
      vo.getTeamNames().add(stringValue(rel.get("teamName")));
    }
  }

  private void fillProjectTeam(ProjectVO row) {
    if (row == null || row.getId() == null) {
      return;
    }
    List<ProjectVO> one = new ArrayList<>();
    one.add(row);
    fillProjectTeams(one);
  }
}
