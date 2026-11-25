package org.dromara.label.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.label.common.MediaType;
import org.dromara.label.common.ImageDimensionUtil;
import org.dromara.label.common.NuscenesMetaSupport;
import org.dromara.label.common.NuscenesMetaSupport.NuscenesIndex;
import org.dromara.label.common.PageResult;
import org.dromara.label.dto.ExportCreateDTO;
import org.dromara.label.dto.NuscenesMetaBundle;
import org.dromara.label.entity.LabelNuscenesAssetMap;
import org.dromara.label.mapper.AnnotationMapper;
import org.dromara.label.mapper.ExportMapper;
import org.dromara.label.mapper.NuscenesMapper;
import org.dromara.label.mapper.ProjectMapper;
import org.dromara.label.service.BaseService;
import org.dromara.label.service.ExportService;
import org.dromara.label.service.MinioService;
import org.dromara.label.service.NuscenesImportService;
import org.dromara.label.util.PointIndicesCodec;
import org.dromara.label.vo.DeliveryRecordVO;
import org.dromara.label.vo.ExportAnnotationVO;
import org.dromara.label.vo.ExportAssetVO;
import org.dromara.label.vo.ExportBatchVO;
import org.dromara.label.vo.ExportDownloadVO;
import org.dromara.label.vo.ExportJobVO;
import org.dromara.label.vo.UploadDataVO;
import org.dromara.label.vo.ExportManifestVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import lombok.extern.slf4j.Slf4j;

/**
 * 导出交付服务实现
 * <p>
 * 导出范围固定为项目「合格数据」：条目标注完成且通过全部审核节点（任务流转至「合格数据」节点后沉淀），
 * 对应 label_data_entry.label_status ∈ labeled/revised 且 review_status=passed。
 * </p>
 * <p>
 * 创建导出为异步流程：校验通过后插入任务（status=processing）并提交后台线程池
 * （装配数据 → 生成 ZIP → 上传 MinIO → 回写状态 success/failed），前端轮询导出任务列表获取进度。
 * 下载地址走 /uploads/** 文件网关（result_url 存网关相对路径）。
 * </p>
 */
@Slf4j
@Service
public class ExportServiceImpl extends BaseService implements ExportService {

    /** 导出格式：平台原生 JSON（assets + annotations，含全部几何类型） */
    private static final String FORMAT_JSON = "JSON";
    /** 导出格式：KITTI 标签（label_2 目录每资产一个 txt，仅框类对象） */
    private static final String FORMAT_KITTI = "KITTI";
    /** 导出格式：nuScenes 原格式（包结构还原 + sample_annotation.json 重写 + 2D COCO） */
    private static final String FORMAT_NUSCENES = "NUSCENES";
    /** 导出格式：点位 JSON（参考外部「无人机」系统结构：数组每帧含 batchId/_id/lensFrame/index/info/size/labels，导出全部 2D 几何） */
    private static final String FORMAT_POINT = "POINT";
    /** 点位导出文件名 */
    private static final String POINT_FILE_NAME = "point_annotations.json";
    /**
     * 导出格式：YOLO（class.json 类别定义 + labels/ 下原图与同名 txt，坐标归一化）。
     * 结构与导入侧约定完全一致，导出包可原样重新导入。
     */
    private static final String FORMAT_YOLO = "YOLO";
    /** YOLO 标签目录：图片与同名 txt 成对放在该目录下 */
    private static final String YOLO_LABELS_DIR = "labels/";
    /** YOLO 类别定义文件名（数组，字段 baseLabel 名称 / label 类别 id / color 颜色） */
    private static final String YOLO_CLASS_FILE = "class.json";
    /** YOLO 标签行：class_id cx cy w h（6 位小数足够还原到像素级） */
    private static final String YOLO_LINE = "%d %.6f %.6f %.6f %.6f";
    /** 平台没有给标签配颜色时的兜底色 */
    private static final String YOLO_DEFAULT_COLOR = "#4f8cff";
    /** YOLO 导出临时文件前缀 / 后缀（含原图二进制，走临时文件避免内存堆积） */
    private static final String YOLO_TEMP_PREFIX = "yolo-export-";
    private static final String YOLO_TEMP_SUFFIX = ".zip";
    /** 媒体类型：图片（与 label_asset.media_type 一致） */
    private static final String MEDIA_TYPE_IMAGE = "image";
    /**
     * 导出格式：COCO（coco.json 标注 + images/ 下原图）。
     * 结构与导入侧约定一致（images 目录 + 单个 COCO json），导出包可原样重新导入。
     */
    private static final String FORMAT_COCO = "COCO";
    /** COCO 导出的图片目录（与导入样本包的 images/ 对齐） */
    private static final String COCO_IMAGES_DIR = "images/";
    /** COCO 导出的标注文件名（与导入样本包的 coco.json 对齐） */
    private static final String COCO_EXPORT_FILE = "coco.json";
    /**
     * 导出格式：文本标注 JSON（annotations.json：条目正文 + 句边界 + 实体 + 情感）。
     * 文本标注不存几何（存全文级字符偏移），独立于视觉/点云格式链路。
     */
    private static final String FORMAT_TEXT = "TEXT";
    /** 文本导出的标注文件名 */
    private static final String TEXT_EXPORT_FILE = "annotations.json";
    /** COCO 导出临时文件前缀 / 后缀（含原图二进制，走临时文件避免内存堆积） */
    private static final String COCO_TEMP_PREFIX = "coco-export-";
    private static final String COCO_TEMP_SUFFIX = ".zip";
    /** COCO 多边形顶点的最少坐标分量数（3 个顶点 → 6 个数） */
    private static final int COCO_MIN_POLYGON_VALUES = 6;

    private static final String STATUS_WAITING = "waiting";
    private static final String STATUS_PROCESSING = "processing";
    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_FAILED = "failed";
    private static final String STATUS_CANCELED = "canceled";

    /** 几何类型：2D 矩形框（x/y/width/height） */
    private static final String KIND_BBOX = "bbox";
    /** 几何类型：2D 多边形（points 顶点数组） */
    private static final String KIND_POLYGON = "polygon";
    /** 几何类型：2D 关键点（单点 x/y/visible） */
    private static final String KIND_KEYPOINT = "keypoint";
    /** 几何类型：2D 骨架关键点（points[{x,y,visible}] + edges[[i,j]]） */
    private static final String KIND_SKELETON = "skeleton";
    /** 几何类型：3D 立体框（center/size/rotationZ） */
    private static final String KIND_CUBOID = "cuboid";
    /** 几何类型：点云实例分割（pointIndices 点云点索引） */
    private static final String KIND_SEGMENTATION = "point_cloud_instance";
    /** 几何类型集合：点云实例分割 / 点云语义分割（均为 pointIndices 点云点索引，导出合并输出） */
    private static final Set<String> KIND_POINT_CLOUD_SEGMENTATIONS = Set.of(KIND_SEGMENTATION, "segmentation");

    /** nuScenes 导出的 2D 标注文件名（COCO 格式） */
    private static final String COCO_FILE_NAME = "coco_annotations.json";
    /** nuScenes 导出的说明文件名 */
    private static final String README_FILE_NAME = "readme.txt";
    /** nuScenes 导出临时文件前缀 / 后缀 */
    private static final String NUSCENES_TEMP_PREFIX = "nuscenes-export-";
    private static final String NUSCENES_TEMP_SUFFIX = ".zip";
    /** zip 流式复制缓冲区大小（字节） */
    private static final int COPY_BUFFER_SIZE = 8192;
    /** 平台属性 sidecar 批量查询分块大小，避免 IN 列表过大 */
    private static final int ATTRIBUTE_QUERY_CHUNK_SIZE = 500;
    /** uploadId 与文件名组合索引的分隔符 */
    private static final String SAMPLE_DATA_REF_SEPARATOR = "|";
    /** lidarseg 缺少 instanceToken 时生成稳定 token 的种子前缀 */
    private static final String LIDARSEG_INSTANCE_SEED = "lidarseg-instance:";
    /** nuScenes token 的十六进制长度（沿用原 token 的判定依据） */
    private static final int NUSCENES_TOKEN_HEX_LENGTH = 32;
    /** nuScenes attribute 表名（导出时需要补充平台自定义属性定义） */
    private static final String TABLE_ATTRIBUTE = "attribute";
    /** nuScenes category / instance 表名（导出时按最终标注重写） */
    private static final String TABLE_CATEGORY = "category";
    private static final String TABLE_INSTANCE = "instance";
    private static final String TABLE_SAMPLE_DATA = "sample_data";
    /** 原包中需要完整还原的媒体目录（含平台未登记为资产的 radar / sweep 文件） */
    private static final Set<String> ORIGINAL_DATA_DIRS = Set.of("samples", "sweeps", "maps");
    /** 类别缺失时的兜底名称 */
    private static final String CATEGORY_FALLBACK_NAME = "object";
    /** 平台扩展属性 sidecar 目录 / 文件名（不污染 nuScenes 标准表） */
    private static final String PLATFORM_ATTRIBUTES_DIR = "platform_attributes";
    private static final String GLOBAL_ATTRS_FILE = "global_attrs.json";
    /** 实例属性中由平台内部使用、不应写入 attribute_tokens 的键（含预标注几何/统计元数据） */
    private static final Set<String> INTERNAL_ATTRIBUTE_KEYS = Set.of(
        "assetId", "classId", "className", "color", "instanceToken", "categoryToken", "trackId",
        "objectKey", "annotationToken", "sampleToken", "sampleDataToken", "source", "visibilityToken",
        "numLidarPts", "numRadarPts", "attributeTokens", "attributeNames", "interpolated",
        "sensorChannel", "categoryName", "globalSize", "globalTranslation", "globalRotation",
        "globalCenter", "globalYaw", "center", "size", "rotation", "rotationZ", "pointIndices",
        "frameIndex", "instanceTotalAnnotations", "instanceMarkedFrames", "instanceFirstFrame",
        "instanceLastFrame", "instanceMissingFrames");
    /** 平台资产 fileUrl 的网关前缀模板（截取 MinIO 对象 Key 用） */
    private static final String FILE_URL_PREFIX_TEMPLATE = "/uploads/";
    /** 平台资产 fileUrl 中 data 段的目录名 */
    private static final String FILE_URL_DATA_SEGMENT = "data/";
    /** 元数据 zipEntry 键（上传时写入 meta_json，导出还原原始路径用） */
    private static final String META_KEY_ZIP_ENTRY = "zipEntry";
    /** 元数据 relativePath 键（直传/文件夹上传写入 meta_json） */
    private static final String META_KEY_RELATIVE_PATH = "relativePath";
    /** COCO iscrowd 固定值（单对象） */
    private static final int COCO_ISCROWD_SINGLE = 0;
    /** COCO categories 的 supercategory 占位值 */
    private static final String COCO_SUPER_CATEGORY = "none";

    /** KITTI 行模板：type truncated occluded alpha x1 y1 x2 y2 l h w x y z ry */
    private static final String KITTI_LINE_2D = "%s -1 -1 -10 %.2f %.2f %.2f %.2f -1 -1 -1 -1000 -1000 -1000 -10";
    private static final String KITTI_LINE_3D = "%s -1 -1 -10 -1 -1 -1 -1 %.2f %.2f %.2f %.2f %.2f %.2f %.2f";

    /** 导出对象在 MinIO 中的根前缀（与 /uploads/** 文件网关映射一致） */
    private static final String EXPORT_OBJECT_PREFIX = "uploads/exports/label/export-";

    @Autowired
    private ExportMapper exportMapper;

    /** 帧级全局属性 / 批注的批量读取（导出平台扩展 sidecar 用） */
    @Autowired
    private AnnotationMapper annotationMapper;

    @Autowired
    private MinioService minioService;

    /** nuScenes 域数据访问（点云-相机映射）与元数据归档读取（原格式导出） */
    @Autowired
    private NuscenesImportService nuscenesImportService;

    @Autowired
    private NuscenesMapper nuscenesMapper;

    /** 上传数据查询（按 uploadId 取文件名作为导出条目文件夹名） */
    @Autowired
    private ProjectMapper projectMapper;

    /** 导出后台处理线程池（装配数据 + ZIP 打包 + MinIO 上传） */
    @Autowired
    @Qualifier("labelExportExecutor")
    private ThreadPoolTaskExecutor exportExecutor;

    @Override
    public PageResult<ExportJobVO> exports() {
        return PageResult.of(exportMapper.selectExports());
    }

    @Override
    public List<ExportBatchVO> exportBatches(Long projectId) {
        if (projectId == null || exportMapper.countProjectById(projectId) == 0) {
            throw new ServiceException("项目不存在或已归档");
        }
        return exportMapper.selectExportableBatches(projectId);
    }

    @Override
    public ExportJobVO createExport(ExportCreateDTO dto) {
        if (dto == null) {
            throw new ServiceException("请选择导出项目");
        }
        Long projectId = dto.getProjectId() != null
                ? dto.getProjectId()
                : findProjectId(dto.getProjectName(), null);
        if (projectId == null || exportMapper.countProjectById(projectId) == 0) {
            throw new ServiceException("项目不存在或已归档");
        }
        String format = dto.getFormat() == null || dto.getFormat().isBlank()
                ? FORMAT_JSON
                : dto.getFormat().trim().toUpperCase(Locale.ROOT);
        if (!FORMAT_JSON.equals(format) && !FORMAT_KITTI.equals(format)
                && !FORMAT_NUSCENES.equals(format) && !FORMAT_POINT.equals(format)
                && !FORMAT_YOLO.equals(format) && !FORMAT_COCO.equals(format)
                && !FORMAT_TEXT.equals(format)) {
            throw new ServiceException("导出格式仅支持 JSON、KITTI、NUSCENES、POINT（点位）、YOLO、COCO 或 TEXT（文本）");
        }
        // 解析导出范围：batchIds 优先（nuScenes 多批次合并导出），否则退化为单个 batchId
        List<Long> batchIds = resolveExportBatchIds(dto);
        if (batchIds.isEmpty()) {
            throw new ServiceException("请选择导出批次");
        }
        // 逐批次校验归属与完成度（全部条目通过全部审核节点方可导出）
        for (Long bid : batchIds) {
            if (exportMapper.countBatchInProject(projectId, bid) == 0) {
                throw new ServiceException("批次不存在或不属于所选项目：" + bid);
            }
            if (exportMapper.countUnfinishedEntries(bid) > 0) {
                throw new ServiceException("批次尚未完成（需全部条目通过全部审核节点）：" + bid);
            }
        }
        // nuScenes 多批次合并导出：多个批次的统一数据集门槛按批次集合统计
        boolean multiBatch = batchIds.size() > 1;
        boolean nuscenesMulti = FORMAT_NUSCENES.equals(format) && multiBatch;
        long qualified = multiBatch
                ? exportMapper.countQualifiedAssetsMulti(projectId, batchIds)
                : exportMapper.countQualifiedAssets(projectId, batchIds.get(0));
        if (qualified == 0) {
            throw new ServiceException("所选批次暂无合格数据：批次内条目需通过全部审核节点后方可导出");
        }
        String batchName = multiBatch ? ("多批次合并(" + batchIds.size() + ")") : exportMapper.selectBatchName(batchIds.get(0));
        long userId = currentUserId();
        Map<String, Object> job = new HashMap<>();
        job.put("projectId", projectId);
        // 批次列存首个批次（兼容旧查询/列表展示）；完整批次集合记入参数供导出与下载预览还原
        job.put("batchId", batchIds.get(0));
        job.put("format", format);
        job.put("status", STATUS_PROCESSING);
        Map<String, Object> params = new HashMap<>();
        params.put("format", format);
        params.put("scope", "qualified");
        params.put("batchId", batchIds.get(0));
        params.put("batchName", batchName);
        params.put("batchIds", batchIds);
        params.put("nuscenesMulti", nuscenesMulti);
        job.put("paramJson", toJson(params));
        job.put("userId", userId);
        // 单条插入自动提交即可（不加事务），确保后台线程能立即读到任务并回写状态
        exportMapper.insertExportJob(job);
        Long generatedId = asLong(job.get("id"));
        if (generatedId == null || generatedId <= 0) {
            throw new ServiceException("创建导出任务失败，请稍后重试");
        }
        long jobId = generatedId;
        writeOperationLog("CREATE_EXPORT", "export", jobId,
                Map.of("format", format, "projectId", projectId, "batchIds", batchIds, "nuscenesMulti", nuscenesMulti));
        try {
            exportExecutor.execute(() -> runExport(jobId, projectId, batchIds, batchName, format, nuscenesMulti, userId));
        } catch (RuntimeException e) {
            // 线程池不可用（如应用关闭中）：回写失败状态，避免任务悬挂在处理中
            exportMapper.updateExportFailed(jobId, truncate("无法提交导出后台任务：" + e.getMessage(), 500), userId);
            throw e;
        }
        return export(jobId);
    }

    /** 解析导出批次集合：batchIds 优先；否则取单个 batchId；均空返回空列表。 */
    private List<Long> resolveExportBatchIds(ExportCreateDTO dto) {
        List<Long> ids = dto.getBatchIds();
        if (ids != null && !ids.isEmpty()) {
            return ids.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        }
        return dto.getBatchId() != null ? List.of(dto.getBatchId()) : List.of();
    }

    @Override
    public ExportJobVO export(Long id) {
        ExportJobVO export = exportMapper.selectExport(id);
        if (export == null) {
            throw new ServiceException("导出任务不存在");
        }
        return export;
    }

    @Override
    public ExportJobVO cancelExport(Long id) {
        exportMapper.updateExportCanceled(id, currentUserId());
        return export(id);
    }

    @Override
    public ExportDownloadVO downloadExport(Long id) {
        ExportJobVO export = export(id);
        String status = export.getStatus();
        if (STATUS_PROCESSING.equals(status) || STATUS_WAITING.equals(status)) {
            throw new ServiceException("导出文件生成中，请稍后再下载");
        }
        if (!STATUS_SUCCESS.equals(status)) {
            String reason = STATUS_FAILED.equals(status) && export.getErrorMessage() != null
                    && !export.getErrorMessage().isBlank()
                    ? "导出失败：" + export.getErrorMessage()
                    : "导出任务未成功完成，无法下载";
            throw new ServiceException(reason);
        }
        ExportDownloadVO result = new ExportDownloadVO();
        result.setId(export.getId());
        result.setStatus(export.getStatus());
        result.setDownloadUrl(export.getResultUrl());
        result.setFileName(buildExportFileName(export.getProjectName(), export.getBatchName()));
        // 多批次导出：从任务参数还原批次集合，保证预览范围与下载包一致
        List<Long> downloadBatchIds = parseBatchIdsFromParam(export.getParamJson());
        result.setManifest(loadQualifiedManifest(export.getProjectId(), export.getProjectName(),
                downloadBatchIds, export.getBatchName(), export.getFormat()));
        return result;
    }

    @Override
    public PageResult<DeliveryRecordVO> deliveryRecords() {
        return PageResult.of(exportMapper.selectDeliveryRecords());
    }

    /**
     * 后台执行导出：装配合格数据 → 生成 ZIP → 上传 MinIO → 回写任务状态并登记交付记录。
     * <p>任何异常均回写 failed（含原因，最长 500 字符），不影响主流程。</p>
     */
    private void runExport(long jobId, Long projectId, List<Long> batchIds, String batchName,
                          String format, boolean nuscenesMulti, long userId) {
        try {
            String projectName = exportMapper.selectProjectName(projectId);
            ExportManifestVO manifest = loadQualifiedManifest(projectId, projectName, batchIds, batchName, format);
            String objectKey = EXPORT_OBJECT_PREFIX + jobId + ".zip";
            if (FORMAT_NUSCENES.equals(format)) {
                // nuScenes 原格式：包含原始 bin/图片二进制，体积大 → 临时文件打包 + 流式上传，避免内存堆积
                uploadNuscenesZip(objectKey, manifest, nuscenesMulti);
            } else if (FORMAT_YOLO.equals(format)) {
                // YOLO 要带原图（否则训练侧拿不到图），同样是二进制大包 → 走临时文件
                uploadYoloZip(objectKey, manifest);
            } else if (FORMAT_COCO.equals(format)) {
                // COCO 同样要带 images/ 下的原图 → 走临时文件
                uploadCocoZip(objectKey, manifest);
            } else if (FORMAT_TEXT.equals(format)) {
                // 文本导出：标注 JSON（正文回读 MinIO + 实体/情感查文本表），纯文本体积小 → 内存打包
                byte[] zip = buildTextZip(projectId, batchIds, batchName);
                try (ByteArrayInputStream data = new ByteArrayInputStream(zip)) {
                    minioService.putObject(objectKey, data, zip.length, "application/zip");
                }
            } else {
                byte[] zip = buildZip(format, manifest);
                try (ByteArrayInputStream data = new ByteArrayInputStream(zip)) {
                    minioService.putObject(objectKey, data, zip.length, "application/zip");
                }
            }
            String downloadUrl = "/" + objectKey;
            if (exportMapper.updateExportSuccess(jobId, downloadUrl, manifest.getAssetCount(), userId) > 0) {
                Map<String, Object> record = new HashMap<>();
                record.put("projectId", projectId);
                record.put("exportJobId", jobId);
                record.put("datasetVersion", "v" + jobId + ".0");
                record.put("format", format);
                record.put("assetCount", manifest.getAssetCount());
                record.put("userId", userId);
                exportMapper.insertDeliveryRecord(record);
            }
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            exportMapper.updateExportFailed(jobId, truncate(message, 500), userId);
        }
    }

    /**
     * 装配项目合格数据清单（资产 + 标注对象，几何/属性/元数据解析为结构化对象）。
     * <p>batchIds 非空时限定批次集合范围（nuScenes 多批次合并导出）；为空（存量任务）退回单批次口径。</p>
     */
    private ExportManifestVO loadQualifiedManifest(Long projectId, String projectName,
                                                   List<Long> batchIds, String batchName, String format) {
        List<ExportAssetVO> assets;
        List<ExportAnnotationVO> objects;
        if (batchIds != null && batchIds.size() > 1) {
            assets = exportMapper.selectExportAssetsMulti(projectId, batchIds);
            objects = exportMapper.selectExportAnnotationsMulti(projectId, batchIds);
        } else {
            Long single = (batchIds != null && !batchIds.isEmpty()) ? batchIds.get(0) : null;
            assets = exportMapper.selectExportAssets(projectId, single, batchIds);
            objects = exportMapper.selectExportAnnotations(projectId, single, batchIds);
        }
        assets.forEach(asset -> asset.setMeta(parseJson(asset.getMetaJson())));
        objects.forEach(object -> {
            object.setGeometry(parseJson(object.getGeometryJson()));
            object.setAttributes(parseJson(object.getAttrJson()));
        });
        ExportManifestVO manifest = new ExportManifestVO();
        manifest.setProjectId(projectId);
        manifest.setProjectName(projectName);
        manifest.setBatchId((batchIds != null && !batchIds.isEmpty()) ? batchIds.get(0) : null);
        manifest.setBatchIds(batchIds);
        manifest.setBatchName(batchName);
        manifest.setFormat(format);
        manifest.setAssetCount(assets.size());
        manifest.setObjectCount(objects.size());
        manifest.setMaskCount(objects.stream()
                .filter(object -> object.getGeometry() instanceof Map<?, ?> geometry
                        && "mask".equals(stringValue(geometry.get("kind"))))
                .count());
        manifest.setAssets(assets);
        manifest.setAnnotations(objects);
        return manifest;
    }

    /** 兼容存量调用：单批次口径装配合格数据清单。 */
    private ExportManifestVO loadQualifiedManifest(Long projectId, String projectName,
                                                   Long batchId, String batchName, String format) {
        return loadQualifiedManifest(projectId, projectName,
                batchId != null ? List.of(batchId) : List.of(), batchName, format);
    }

    /**
     * 生成导出 ZIP：JSON 格式为 annotations.json + readme.txt；
     * KITTI 格式为 label_2/{资产名}.txt（每资产一个标签文件）+ readme.txt。
     */
    private byte[] buildZip(String format, ExportManifestVO manifest) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        int skipped = 0;
        if (FORMAT_JSON.equals(format)) {
            entries.put("annotations.json", toJsonPretty(manifest));
        } else if (FORMAT_POINT.equals(format)) {
            PointResult point = buildPointResult(manifest);
            skipped = point.skipped();
            entries.put(POINT_FILE_NAME, toJsonPretty(point.frames()));
        } else {
            KittiResult kitti = buildKittiEntries(manifest);
            skipped = kitti.skipped();
            entries.putAll(kitti.files());
        }
        entries.put("readme.txt", buildReadme(format, manifest, skipped));
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return buffer.toByteArray();
    }

    /**
     * 文本导出（TEXT 格式）：annotations.json = {projectId, projectName, batchName, entryCount,
     * entries:[{entryId, entryNo, entryName, fileName, text, sentences:[[s,e]...],
     * entities:[{entityKey,classId,className,color,startOffset,endOffset,entityText,sentenceIndex}],
     * sentiments:[{levelType,sentenceIndex,classId,className,color,remark}]}]} + readme.txt。
     *
     * <p>正文按资产 file_url 回读 MinIO（字符偏移的基准文本必须导出，否则偏移不可用）；
     * 句边界从资产 meta_json.bounds（扁平数组 [s0,e0,s1,e1,...]）转成成对数组。
     * 实体/情感按 annotation.asset_id 归组到条目，含软删除过滤，与标注页展示同一套口径。</p>
     */
    private byte[] buildTextZip(Long projectId, List<Long> batchIds, String batchName) throws IOException {
        List<Map<String, Object>> entries = exportMapper.selectExportTextEntries(projectId, batchIds);
        List<Map<String, Object>> entities = exportMapper.selectExportTextEntities(projectId, batchIds);
        List<Map<String, Object>> sentiments = exportMapper.selectExportTextSentiments(projectId, batchIds);

        // 按资产归组实体与情感（一个文本条目一条文本资产）
        Map<Long, List<Map<String, Object>>> entitiesByAsset = new HashMap<>();
        for (Map<String, Object> e : entities) {
            entitiesByAsset.computeIfAbsent(asLong(e.get("assetId")), k -> new ArrayList<>()).add(e);
        }
        Map<Long, List<Map<String, Object>>> sentimentsByAsset = new HashMap<>();
        for (Map<String, Object> s : sentiments) {
            sentimentsByAsset.computeIfAbsent(asLong(s.get("assetId")), k -> new ArrayList<>()).add(s);
        }

        List<Map<String, Object>> outEntries = new ArrayList<>();
        for (Map<String, Object> entry : entries) {
            Long assetId = asLong(entry.get("assetId"));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("entryId", entry.get("entryId"));
            out.put("entryNo", entry.get("entryNo"));
            out.put("entryName", entry.get("entryName"));
            out.put("fileName", entry.get("fileName"));
            // 正文：实体偏移的基准文本，逐条回读（文本文件 KB 级，量级可控）
            out.put("text", readExportText(asString(entry.get("fileUrl"))));
            out.put("sentences", parseExportSentences(asString(entry.get("metaJson"))));
            out.put("entities", entitiesByAsset.getOrDefault(assetId, List.of()));
            out.put("sentiments", sentimentsByAsset.getOrDefault(assetId, List.of()));
            outEntries.add(out);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        result.put("projectName", exportMapper.selectProjectName(projectId));
        result.put("batchName", batchName);
        result.put("entryCount", outEntries.size());
        result.put("entries", outEntries);

        Map<String, String> files = new LinkedHashMap<>();
        files.put(TEXT_EXPORT_FILE, toJsonPretty(result));
        files.put("readme.txt", "文本标注导出（TEXT 格式）\n"
            + "annotations.json：entries[].text 为条目正文；entities[].startOffset/endOffset 为\n"
            + "全文级字符偏移（含/不含，等价于 text.substring(start, end)）；sentences 为句边界\n"
            + "[start,end] 成对数组；sentiments[].levelType=entry 为条目级、sentence 为句级\n"
            + "（sentenceIndex 对应 sentences 下标）。\n");

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> f : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(f.getKey()));
                zip.write(f.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return buffer.toByteArray();
    }

    /** 回读文本资产正文（与标注页 readText 同款：MinIO 流 → UTF-8 字符串）。 */
    private String readExportText(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return "";
        }
        try (InputStream in = minioService.openObject(fileUrl.startsWith("/") ? fileUrl.substring(1) : fileUrl)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ServiceException("读取文本内容失败：" + fileUrl);
        }
    }

    /** 句边界解析：meta_json.bounds 扁平数组 [s0,e0,s1,e1,...] → [[s0,e0],[s1,e1],...]。 */
    private List<List<Integer>> parseExportSentences(String metaJson) {
        List<List<Integer>> sentences = new ArrayList<>();
        if (metaJson == null || metaJson.isBlank()) {
            return sentences;
        }
        try {
            JsonNode array = objectMapper.readTree(metaJson).get("bounds");
            if (array == null || !array.isArray()) {
                return sentences;
            }
            for (int i = 0; i + 1 < array.size(); i += 2) {
                sentences.add(List.of(array.get(i).asInt(), array.get(i + 1).asInt()));
            }
            return sentences;
        } catch (Exception e) {
            // 元信息损坏不阻断导出：句边界缺失仅影响句级情感定位，实体偏移不受影响
            log.warn("解析句边界失败，导出条目 sentences 为空: {}", e.getMessage());
            return sentences;
        }
    }

    private String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    /**
     * 装配 KITTI 标签文件：仅导出框类对象（2D bbox / 3D cuboid），其余几何类型跳过并计数。
     * <p>同资产文件名去后缀重名时追加 -{assetId} 后缀避免覆盖。</p>
     */
    private KittiResult buildKittiEntries(ExportManifestVO manifest) {
        Map<Long, String> fileNames = new LinkedHashMap<>();
        Map<Long, StringBuilder> lines = new LinkedHashMap<>();
        Map<String, Boolean> usedNames = new HashMap<>();
        for (ExportAssetVO asset : manifest.getAssets()) {
            String stem = stripExtension(asset.getFileName());
            String name = usedNames.putIfAbsent(stem, Boolean.TRUE) == null ? stem : stem + "-" + asset.getAssetId();
            fileNames.put(asset.getAssetId(), name);
            lines.put(asset.getAssetId(), new StringBuilder());
        }
        int skipped = 0;
        for (ExportAnnotationVO object : manifest.getAnnotations()) {
            String line = toKittiLine(object);
            if (line == null) {
                skipped++;
                continue;
            }
            StringBuilder builder = lines.get(object.getAssetId());
            if (builder != null) {
                builder.append(line).append('\n');
            }
        }
        Map<String, String> files = new LinkedHashMap<>();
        for (Map.Entry<Long, StringBuilder> entry : lines.entrySet()) {
            files.put("label_2/" + fileNames.get(entry.getKey()) + ".txt", entry.getValue().toString());
        }
        return new KittiResult(files, skipped);
    }

    /**
     * 标注对象转 KITTI 标签行（15 列）：
     * 2D 矩形框仅 bbox 四列有效（3D 列为无效占位）；3D 立体框 l/h/w 对应 size.x/z/y，
     * location 为框底部中心（center.z - h/2），ry 为绕竖直轴偏航角（保持平台 Z 轴向上坐标系，未做相机系变换）。
     * 非框类几何返回 null（由调用方跳过计数）。
     */
    private String toKittiLine(ExportAnnotationVO object) {
        if (!(object.getGeometry() instanceof Map<?, ?> geometry)) {
            return null;
        }
        String kind = stringValue(geometry.get("kind"));
        String type = object.getClassName() == null || object.getClassName().isBlank()
                ? "object"
                : object.getClassName().trim();
        if (KIND_BBOX.equals(kind)) {
            double x = asNumber(geometry.get("x")).doubleValue();
            double y = asNumber(geometry.get("y")).doubleValue();
            double width = asNumber(geometry.get("width")).doubleValue();
            double height = asNumber(geometry.get("height")).doubleValue();
            return String.format(Locale.ROOT, KITTI_LINE_2D, type, x, y, x + width, y + height);
        }
        if (KIND_CUBOID.equals(kind)
                && geometry.get("center") instanceof Map<?, ?> center
                && geometry.get("size") instanceof Map<?, ?> size) {
            double length = asNumber(size.get("x")).doubleValue();
            double height = asNumber(size.get("z")).doubleValue();
            double width = asNumber(size.get("y")).doubleValue();
            double x = asNumber(center.get("x")).doubleValue();
            double y = asNumber(center.get("y")).doubleValue();
            double z = asNumber(center.get("z")).doubleValue() - height / 2;
            double yaw = asNumber(geometry.get("rotationZ")).doubleValue();
            return String.format(Locale.ROOT, KITTI_LINE_3D, type, length, height, width, x, y, z, yaw);
        }
        return null;
    }

    // ---------------- 点位（POINT）导出 ----------------

    /** 点位导出装配结果：帧对象数组 + 不支持几何的对象跳过数 */
    private record PointResult(List<Map<String, Object>> frames, int skipped) {
    }

    /**
     * 装配参考格式导出帧数组：每帧一个对象，结构对齐外部「无人机」参考系统。
     * <p>导出全部 2D 几何（矩形框 / 多边形 / 关键点 / 骨架），其余几何类型跳过并计数；图片资产读取像素宽高填入 size，
     * 点云等无尺寸类型 size 置 0。帧 batchId 取导出批次（导出按批次粒度，与 KITTI 一致）。</p>
     */
    private PointResult buildPointResult(ExportManifestVO manifest) {
        List<Map<String, Object>> frames = new ArrayList<>();
        int skipped = 0;
        // 资产ID -> 图片尺寸（懒读取并缓存，避免同资产重复下载）
        Map<Long, int[]> sizeCache = new HashMap<>();
        // 标注按资产聚合
        Map<Long, List<ExportAnnotationVO>> byAsset = new LinkedHashMap<>();
        for (ExportAnnotationVO ann : manifest.getAnnotations()) {
            byAsset.computeIfAbsent(ann.getAssetId(), k -> new ArrayList<>()).add(ann);
        }
        int frameIndex = 0;
        for (ExportAssetVO asset : manifest.getAssets()) {
            Long assetId = asset.getAssetId();
            Map<String, Object> frame = new LinkedHashMap<>();
            // 帧结构对齐外部「无人机」参考系统：batchId / _id / lensFrame / index / info / size / labels
            // _id 保留平台内部资产 ID（数值），便于与系统内部数据回查对应
            frame.put("batchId", manifest.getBatchId());
            frame.put("_id", assetId);
            frame.put("lensFrame", -0.5);
            frame.put("index", frameIndex);
            frame.put("info", asset.getFileUrl());
            // 尺寸：优先用入库时预存的宽高；历史数据（未存）则回读图片（仅图片类型），其余置 0
            int[] size = null;
            Integer w = asset.getWidth();
            Integer h = asset.getHeight();
            if (w != null && w > 0 && h != null && h > 0) {
                size = new int[] { w, h };
            } else if (MediaType.IMAGE.equals(asset.getMediaType())) {
                size = sizeCache.computeIfAbsent(assetId, k -> readImageSize(asset.getFileUrl()));
            }
            Map<String, Object> sizeMap = new LinkedHashMap<>();
            sizeMap.put("width", size == null ? 0 : size[0]);
            sizeMap.put("height", size == null ? 0 : size[1]);
            frame.put("size", sizeMap);
            // 标注：导出全部 2D 几何（矩形框 / 多边形 / 关键点 / 骨架），其余几何跳过计数
            List<Map<String, Object>> labels = new ArrayList<>();
            List<ExportAnnotationVO> anns = byAsset.getOrDefault(assetId, List.of());
            int labelSeq = 0;
            for (ExportAnnotationVO ann : anns) {
                Map<String, Object> label = toReferenceLabel(ann, labelSeq);
                if (label == null) {
                    skipped++;
                    continue;
                }
                labels.add(label);
                labelSeq++;
            }
            frame.put("labels", labels);
            frames.add(frame);
            frameIndex++;
        }
        return new PointResult(frames, skipped);
    }

    /**
     * 标注对象转参考系统 label：结构对齐外部「无人机」参考系统。
     * <p>导出全部 2D 几何类型：矩形框（bbox → RECTANGLE）、多边形（polygon → POLYGON）、
     * 关键点（keypoint → point）、骨架（skeleton → point）；points 为扁平坐标数组
     * [x1,y1, x2,y2, ...]（与参考系统一致）。3D 几何（立体框 / 点云分割）与栅格掩码无矢量表示，跳过返回 null。</p>
     */
    private Map<String, Object> toReferenceLabel(ExportAnnotationVO ann, int seq) {
        if (!(ann.getGeometry() instanceof Map<?, ?> geometry)) {
            return null;
        }
        String kind = stringValue(geometry.get("kind"));
        String drawType;
        // 坐标保留原始精度（与参考系统一致，不做四舍五入），直接写入 double 原值
        List<Double> flat = new ArrayList<>();
        if (KIND_BBOX.equals(kind)) {
            // 矩形框：x/y/width/height → 左上角与右下角两顶点
            double x = asNumber(geometry.get("x")).doubleValue();
            double y = asNumber(geometry.get("y")).doubleValue();
            double w = asNumber(geometry.get("width")).doubleValue();
            double h = asNumber(geometry.get("height")).doubleValue();
            flat.add(x);
            flat.add(y);
            flat.add(x + w);
            flat.add(y + h);
            drawType = "RECTANGLE";
        } else if (KIND_POLYGON.equals(kind) && geometry.get("points") instanceof List<?> pts) {
            // 多边形：全部顶点扁平输出
            for (Object p : pts) {
                if (!(p instanceof Map<?, ?> pm)) {
                    continue;
                }
                flat.add(asNumber(pm.get("x")).doubleValue());
                flat.add(asNumber(pm.get("y")).doubleValue());
            }
            drawType = "POLYGON";
        } else if (KIND_KEYPOINT.equals(kind)) {
            // 关键点：单点（x/y）或模板点集（points），统一扁平
            if (geometry.get("x") != null && geometry.get("y") != null) {
                flat.add(asNumber(geometry.get("x")).doubleValue());
                flat.add(asNumber(geometry.get("y")).doubleValue());
            } else if (geometry.get("points") instanceof List<?> pts) {
                for (Object p : pts) {
                    if (!(p instanceof Map<?, ?> pm)) {
                        continue;
                    }
                    flat.add(asNumber(pm.get("x")).doubleValue());
                    flat.add(asNumber(pm.get("y")).doubleValue());
                }
            }
            drawType = "point";
        } else if (KIND_SKELETON.equals(kind) && geometry.get("points") instanceof List<?> pts) {
            // 骨架关键点：按模板顺序输出全部点（扁平）
            for (Object p : pts) {
                if (!(p instanceof Map<?, ?> pm)) {
                    continue;
                }
                flat.add(asNumber(pm.get("x")).doubleValue());
                flat.add(asNumber(pm.get("y")).doubleValue());
            }
            drawType = "point";
        } else {
            // 3D 几何 / 栅格掩码等无矢量表示，跳过
            return null;
        }
        if (flat.isEmpty()) {
            return null;
        }
        Map<String, Object> label = new LinkedHashMap<>();
        label.put("_id", ann.getAnnotationId());
        label.put("id", seq + 1);
        label.put("label", ann.getClassName() == null || ann.getClassName().isBlank() ? "object" : ann.getClassName());
        label.put("drawType", drawType);
        label.put("group", 0);
        label.put("points", flat);
        label.put("zIndex", seq + 1);
        Object attr = ann.getAttributes();
        label.put("attr", attr instanceof Map<?, ?> ? attr : Map.of());
        return label;
    }

    /** 读取图片像素尺寸（宽/高）；fileUrl 非网关路径或无图时返回 null（size 置 0）。 */
    private int[] readImageSize(String fileUrl) {
        if (fileUrl == null || !fileUrl.startsWith(FILE_URL_PREFIX_TEMPLATE)) {
            return null;
        }
        try (InputStream in = minioService.openObject(fileUrl.substring(1))) {
            return ImageDimensionUtil.read(in);
        } catch (Exception e) {
            log.warn("读取图片尺寸失败（size 将置 0）：{}", fileUrl, e);
        }
        return null;
    }

    // ---------------- nuScenes 原格式导出 ----------------

    /**
     * nuScenes 原格式导出：临时文件打包后流式上传 MinIO（含原始 bin/图片二进制，体积大）。
     */
    private void uploadNuscenesZip(String objectKey, ExportManifestVO manifest, boolean nuscenesMulti) throws IOException {
        Path tempFile = Files.createTempFile(NUSCENES_TEMP_PREFIX, NUSCENES_TEMP_SUFFIX);
        try {
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(tempFile), StandardCharsets.UTF_8)) {
                writeNuscenesZip(zip, manifest, nuscenesMulti);
            }
            try (InputStream in = Files.newInputStream(tempFile)) {
                minioService.putObject(objectKey, in, Files.size(tempFile), "application/zip");
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * 装配 nuScenes 原格式导出包：按条目（上传数据）分组，每个条目一个文件夹，
     * 文件夹内是该条目独立的 nuScenes 格式（数据文件 + 元数据归档 + 重写 sample_annotation + COCO）。
     * <p>每个条目对应一次 nuScenes 上传（一个完整包），导出时还原为：
     * 1. 数据文件：条目下资产按上传时的原始相对路径（去掉原包顶层目录）写入其文件夹；
     * 2. 元数据：该条目归档的 v1.0-* JSON 原样复制（sample_annotation.json 除外）；
     * 3. sample_annotation.json 重写：该条目覆盖的样本用平台标注（sensor -&gt; global 逆变换），其余样本原样保留；
     * 4. 2D 图片标注：该条目图片资产的 COCO（coco_annotations.json）；
     * 5. readme.txt 整包一份（放根）。</p>
     */
    private void writeNuscenesZip(ZipOutputStream zip, ExportManifestVO manifest, boolean nuscenesMulti) throws IOException {
        if (nuscenesMulti) {
            // nuScenes 多批次合并导出：将多个批次合并为单一 nuScenes 数据集（统一 v1.0-* 目录 + sample_annotation）
            writeNuscenesZipUnified(zip, manifest);
            return;
        }
        NuscenesExportContext context = buildNuscenesContext(manifest);
        // 按上传数据（条目）分组：每个条目一个文件夹，文件夹内是该条目独立的 nuScenes 格式
        Map<Long, List<ExportAssetVO>> assetsByUpload = new LinkedHashMap<>();
        for (ExportAssetVO asset : manifest.getAssets()) {
            assetsByUpload.computeIfAbsent(asset.getUploadId(), k -> new ArrayList<>()).add(asset);
        }
        Set<String> writtenEntries = new HashSet<>();
        for (Map.Entry<Long, List<ExportAssetVO>> en : assetsByUpload.entrySet()) {
            Long uploadId = en.getKey();
            List<ExportAssetVO> groupAssets = en.getValue();
            String folder = folderNameOf(uploadId, groupAssets);
            // 1. 数据文件还原（去掉原包顶层目录前缀，归一到该条目文件夹下）
            for (ExportAssetVO asset : groupAssets) {
                String rel = stripTopDir(originalRelativePath(asset));
                if (rel.isEmpty()) {
                    continue;
                }
                String entryName = folder + "/" + rel;
                if (!writtenEntries.add(entryName)) {
                    continue;
                }
                copyAssetFile(zip, entryName, asset.getFileUrl());
            }
            if (uploadId != null) {
                // 平台资产表可能未登记 RADAR/sweep，但 sample_data 仍引用它们；优先从原始上传 ZIP 补齐。
                copyOriginalDataFilesFromSourceZip(zip, uploadId, context, folder, writtenEntries);
            }
            if (uploadId == null) {
                // 无上传数据归属（非 nuScenes 资产）：仅导出数据文件与图片 COCO，跳过元数据与重写
                writeCocoAnnotationsForUpload(zip, folder, groupAssets, manifest.getAnnotations());
                continue;
            }
            // 2. 元数据 JSON 原样复制（仅该条目归档；sample_annotation.json 由重写步骤生成）
            writeNuscenesMetaEntriesForUpload(zip, uploadId, context, writtenEntries, folder);
            // 3. sample_annotation.json 重写（仅该条目有元数据归档时）
            if (context.bundleByUpload.containsKey(uploadId)) {
                writeSampleDataJsonForUpload(zip, uploadId, context, folder, writtenEntries);
                List<Map<String, Object>> rows = buildRewrittenSampleAnnotationsForUpload(
                        manifest, context, uploadId, groupAssetIds(groupAssets));
                String saDir = context.sampleAnnotationDirByUpload.get(uploadId);
                if (saDir == null || saDir.isBlank()) {
                    saDir = stripTopDir(versionDirOfUpload(context, uploadId));
                }
                String sampleAnnotationEntry = saDir.isBlank()
                        ? folder + "/" + NuscenesMetaSupport.TABLE_SAMPLE_ANNOTATION + ".json"
                        : folder + "/" + saDir + "/" + NuscenesMetaSupport.TABLE_SAMPLE_ANNOTATION + ".json";
                writeJsonEntry(zip, sampleAnnotationEntry, rows);
                writeAttributeJsonForUpload(zip, uploadId, context, folder);
                writeCategoryJsonForUpload(zip, uploadId, context, folder);
                writeInstanceJsonForUpload(zip, uploadId, context, rows, folder);
            }
            // 4. 2D 图片标注 → COCO（仅该条目图片资产）
            writeCocoAnnotationsForUpload(zip, folder, groupAssets, manifest.getAnnotations());
            // 5. 点云实例分割（geometry.kind=point_cloud_instance 的 pointIndices）→ lidarseg/{sampleDataToken}_segmentation.json
            writePointCloudSegmentationForUpload(zip, folder, context, groupAssetIds(groupAssets), manifest);
        }
        // 6. 平台扩展属性（帧级全局属性 / 无效帧原因 / 批注）
        writePlatformAttributesSidecar(zip, manifest, context);
        // 7. 说明文件（整包一份，放根）
        int skipped = countUnmappedCuboids(manifest, context);
        writeTextEntry(zip, README_FILE_NAME, buildReadme(FORMAT_NUSCENES, manifest, skipped));
    }

    // ---------------- YOLO 导出 ----------------

    /**
     * YOLO 导出：要带原图（否则训练侧拿不到图），是二进制大包
     * → 同 nuScenes 走临时文件打包 + 流式上传，避免内存堆积。
     */
    private void uploadYoloZip(String objectKey, ExportManifestVO manifest) throws IOException {
        Path tempFile = Files.createTempFile(YOLO_TEMP_PREFIX, YOLO_TEMP_SUFFIX);
        try {
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(tempFile), StandardCharsets.UTF_8)) {
                writeYoloZip(zip, manifest);
            }
            try (InputStream in = Files.newInputStream(tempFile)) {
                minioService.putObject(objectKey, in, Files.size(tempFile), "application/zip");
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * 装配 YOLO 数据包，结构与导入侧约定完全一致（导出包可原样重新导入）：
     * <ol>
     *   <li>{@code class.json}：类别定义数组，字段 baseLabel（名称）/ label（类别 id）/ color（颜色）；</li>
     *   <li>{@code labels/{图片名}.jpg}：原图；</li>
     *   <li>{@code labels/{图片名}.txt}：每行 {@code class_id cx cy w h}，相对原图宽高归一化。</li>
     * </ol>
     * 仅导出 2D 矩形框——YOLO 检测格式只支持矩形；其余几何跳过并计入 readme 的 skipped。
     */
    private void writeYoloZip(ZipOutputStream zip, ExportManifestVO manifest) throws IOException {
        // 1. 图片资产的稳定文件名（同名去重，口径与 KITTI 一致）
        Map<Long, String> stems = new LinkedHashMap<>();
        Map<String, Boolean> usedNames = new HashMap<>();
        for (ExportAssetVO asset : manifest.getAssets()) {
            if (!isImageAsset(asset)) {
                continue;
            }
            String stem = stripExtension(asset.getFileName());
            stems.put(asset.getAssetId(), usedNames.putIfAbsent(stem, Boolean.TRUE) == null
                    ? stem : stem + "-" + asset.getAssetId());
        }
        // 2. 逐框生成 txt 行；类别 id 按出现顺序从 0 连续编号（YOLO 要求 id 连续且从 0 起）
        Map<Long, Integer> classIndex = new LinkedHashMap<>();
        List<Map<String, Object>> classDefs = new ArrayList<>();
        Map<Long, int[]> sizes = new LinkedHashMap<>();
        Map<Long, StringBuilder> lines = new LinkedHashMap<>();
        for (ExportAssetVO asset : manifest.getAssets()) {
            if (stems.containsKey(asset.getAssetId())) {
                sizes.put(asset.getAssetId(), sizeOfAsset(asset));
                lines.put(asset.getAssetId(), new StringBuilder());
            }
        }
        int skipped = 0;
        for (ExportAnnotationVO object : manifest.getAnnotations()) {
            StringBuilder builder = lines.get(object.getAssetId());
            if (builder == null) {
                continue;
            }
            String line = toYoloLine(object, sizes.get(object.getAssetId()), classIndex, classDefs);
            if (line == null) {
                skipped++;
                continue;
            }
            builder.append(line).append('\n');
        }
        // 3. class.json 放最前，便于读包方先拿到类别定义
        writeJsonEntry(zip, YOLO_CLASS_FILE, classDefs);
        // 4. labels/ 下图片与同名 txt 成对写出（没有标注的图也要出图，只给空 txt）
        for (ExportAssetVO asset : manifest.getAssets()) {
            String stem = stems.get(asset.getAssetId());
            if (stem == null) {
                continue;
            }
            copyAssetFile(zip, YOLO_LABELS_DIR + stem + extensionOf(asset.getFileName()), asset.getFileUrl());
            StringBuilder builder = lines.get(asset.getAssetId());
            writeTextEntry(zip, YOLO_LABELS_DIR + stem + ".txt", builder == null ? "" : builder.toString());
        }
        writeTextEntry(zip, README_FILE_NAME, buildReadme(FORMAT_YOLO, manifest, skipped));
    }

    /**
     * 标注对象转 YOLO 标签行（class_id cx cy w h，归一化到 0~1）。
     * 仅 2D 矩形框可导出；缺原图尺寸或非框类几何返回 null（由调用方跳过计数）。
     */
    private String toYoloLine(ExportAnnotationVO object, int[] size,
                              Map<Long, Integer> classIndex, List<Map<String, Object>> classDefs) {
        if (size == null || size.length < 2 || size[0] <= 0 || size[1] <= 0) {
            return null;
        }
        if (!(object.getGeometry() instanceof Map<?, ?> geometry)
                || !KIND_BBOX.equals(stringValue(geometry.get("kind")))) {
            return null;
        }
        double x = asNumber(geometry.get("x")).doubleValue();
        double y = asNumber(geometry.get("y")).doubleValue();
        double w = asNumber(geometry.get("width")).doubleValue();
        double h = asNumber(geometry.get("height")).doubleValue();
        int index = classIndexOf(object, classIndex, classDefs);
        return String.format(Locale.ROOT, YOLO_LINE, index,
                (x + w / 2) / size[0], (y + h / 2) / size[1],
                w / size[0], h / size[1]);
    }

    /**
     * 类别 id：按出现顺序从 0 连续编号。平台的 classId 是数据库主键、数值大且不连续，
     * 直接写进 txt 不符合 YOLO 约定，这里映射一份，对应关系由 class.json 给出。
     */
    private int classIndexOf(ExportAnnotationVO object, Map<Long, Integer> classIndex,
                             List<Map<String, Object>> classDefs) {
        Integer index = classIndex.get(object.getClassId());
        if (index != null) {
            return index;
        }
        index = classDefs.size();
        classIndex.put(object.getClassId(), index);
        Map<String, Object> def = new LinkedHashMap<>();
        def.put("baseLabel", classNameOf(object));
        def.put("label", index);
        def.put("color", object.getColor() == null || object.getColor().isBlank()
                ? YOLO_DEFAULT_COLOR : object.getColor().trim());
        classDefs.add(def);
        return index;
    }

    /**
     * 图片资产的像素尺寸：优先入库宽高，缺失时回读图片文件。
     * 与导入侧还原归一化坐标用的是同一对值，保证「导出 → 重新导入」闭环一致。
     */
    private int[] sizeOfAsset(ExportAssetVO asset) {
        Integer w = asset.getWidth();
        Integer h = asset.getHeight();
        if (w != null && h != null && w > 0 && h > 0) {
            return new int[] {w, h};
        }
        return readImageSize(asset.getFileUrl());
    }

    /** 是否图片资产：YOLO 只导出图片（点云没有原图尺寸，无法归一化） */
    private boolean isImageAsset(ExportAssetVO asset) {
        String type = asset.getMediaType();
        if (type != null && !type.isBlank()) {
            return MEDIA_TYPE_IMAGE.equals(type.trim());
        }
        String name = asset.getFileName() == null ? "" : asset.getFileName().toLowerCase(Locale.ROOT);
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
                || name.endsWith(".bmp") || name.endsWith(".webp") || name.endsWith(".gif");
    }

    /** 取文件扩展名（含点）：导出图片要保留原扩展名；无扩展名返回空串 */
    private String extensionOf(String fileName) {
        String name = fileName == null ? "" : fileName.trim();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot) : "";
    }

    /** 类别名兜底：无名时用 class_{classId}，保证 class.json 里名称不为空 */
    private String classNameOf(ExportAnnotationVO object) {
        String name = object.getClassName();
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        return "class_" + object.getClassId();
    }

    // ---------------- COCO 导出 ----------------

    /**
     * COCO 导出：要带 images/ 下的原图 → 同 YOLO 走临时文件打包 + 流式上传，避免内存堆积。
     */
    private void uploadCocoZip(String objectKey, ExportManifestVO manifest) throws IOException {
        Path tempFile = Files.createTempFile(COCO_TEMP_PREFIX, COCO_TEMP_SUFFIX);
        try {
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(tempFile), StandardCharsets.UTF_8)) {
                writeCocoZip(zip, manifest);
            }
            try (InputStream in = Files.newInputStream(tempFile)) {
                minioService.putObject(objectKey, in, Files.size(tempFile), "application/zip");
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * 装配 COCO 数据包，结构与导入侧约定一致（images/ 下原图 + 单个 coco.json，可原样重新导入）：
     * <ul>
     *   <li>{@code images[]}：id（从 1 起）/ width / height / file_name（纯文件名，与资产同名）；</li>
     *   <li>{@code categories[]}：id（从 0 起连续）/ name / supercategory；</li>
     *   <li>{@code annotations[]}：id / image_id / category_id / bbox（绝对像素 x,y,w,h）/ area / iscrowd / segmentation。</li>
     * </ul>
     * 矩形框写 bbox 且 segmentation 置空；多边形写扁平顶点并用其外接矩形作 bbox；
     * 其余几何（关键点 / 立体框 / 点云分割）跳过并计入 readme 的 skipped。
     */
    private void writeCocoZip(ZipOutputStream zip, ExportManifestVO manifest) throws IOException {
        // 1. 图片资产清单（同名去重，口径与 KITTI / YOLO 一致）
        List<ExportAssetVO> imageAssets = new ArrayList<>();
        Map<String, Boolean> usedNames = new HashMap<>();
        for (ExportAssetVO asset : manifest.getAssets()) {
            if (!isImageAsset(asset)) {
                continue;
            }
            usedNames.putIfAbsent(stripExtension(asset.getFileName()), Boolean.TRUE);
            imageAssets.add(asset);
        }
        // 2. images[]：id 从 1 起；像素尺寸缺失时置 0（COCO 允许，导入侧按文件名匹配）
        Map<Long, Integer> imageIds = new LinkedHashMap<>();
        List<Map<String, Object>> images = new ArrayList<>();
        for (ExportAssetVO asset : imageAssets) {
            int[] size = sizeOfAsset(asset);
            int imageId = images.size() + 1;
            imageIds.put(asset.getAssetId(), imageId);
            Map<String, Object> image = new LinkedHashMap<>();
            image.put("id", imageId);
            image.put("width", size == null ? 0 : size[0]);
            image.put("height", size == null ? 0 : size[1]);
            image.put("file_name", asset.getFileName());
            images.add(image);
        }
        // 3. annotations[] 与 categories[]：类别 id 按出现顺序从 0 连续编号
        Map<Long, Integer> classIndex = new LinkedHashMap<>();
        List<Map<String, Object>> categories = new ArrayList<>();
        List<Map<String, Object>> annotations = new ArrayList<>();
        int skipped = 0;
        for (ExportAnnotationVO object : manifest.getAnnotations()) {
            Integer imageId = imageIds.get(object.getAssetId());
            if (imageId == null) {
                continue;
            }
            Map<String, Object> ann = toCocoAnnotation(object, imageId, classIndex, categories);
            if (ann == null) {
                skipped++;
                continue;
            }
            ann.put("id", annotations.size() + 1);
            annotations.add(ann);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("images", images);
        root.put("categories", categories);
        root.put("annotations", annotations);
        writeJsonEntry(zip, COCO_EXPORT_FILE, root);
        // 4. images/ 下原图（没有标注的图也要出图，COCO 里它仍是合法负样本）
        for (ExportAssetVO asset : imageAssets) {
            copyAssetFile(zip, COCO_IMAGES_DIR + asset.getFileName(), asset.getFileUrl());
        }
        writeTextEntry(zip, README_FILE_NAME, buildReadme(FORMAT_COCO, manifest, skipped));
    }

    /**
     * 标注对象转 COCO annotation（不含 id，由调用方按序补）。
     * 矩形框：bbox 直接取用，segmentation 置空数组；
     * 多边形：扁平顶点写入 segmentation，bbox 取其外接矩形。
     * 其他几何返回 null（由调用方跳过计数）。
     */
    private Map<String, Object> toCocoAnnotation(ExportAnnotationVO object, int imageId,
                                                 Map<Long, Integer> classIndex,
                                                 List<Map<String, Object>> categories) {
        if (!(object.getGeometry() instanceof Map<?, ?> geometry)) {
            return null;
        }
        String kind = stringValue(geometry.get("kind"));
        double[] bbox;
        List<List<Double>> segmentation = new ArrayList<>();
        if (KIND_BBOX.equals(kind)) {
            bbox = new double[] {
                asNumber(geometry.get("x")).doubleValue(),
                asNumber(geometry.get("y")).doubleValue(),
                asNumber(geometry.get("width")).doubleValue(),
                asNumber(geometry.get("height")).doubleValue()
            };
        } else if (KIND_POLYGON.equals(kind) && geometry.get("points") instanceof List<?> pts) {
            List<Double> flat = new ArrayList<>();
            double minX = Double.MAX_VALUE;
            double minY = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE;
            double maxY = -Double.MAX_VALUE;
            for (Object item : pts) {
                if (!(item instanceof Map<?, ?> p)) {
                    continue;
                }
                double px = asNumber(p.get("x")).doubleValue();
                double py = asNumber(p.get("y")).doubleValue();
                flat.add(NuscenesMetaSupport.round4(px));
                flat.add(NuscenesMetaSupport.round4(py));
                minX = Math.min(minX, px);
                minY = Math.min(minY, py);
                maxX = Math.max(maxX, px);
                maxY = Math.max(maxY, py);
            }
            if (flat.size() < COCO_MIN_POLYGON_VALUES) {
                return null;
            }
            segmentation.add(flat);
            bbox = new double[] {minX, minY, maxX - minX, maxY - minY};
        } else {
            return null;
        }
        Map<String, Object> ann = new LinkedHashMap<>();
        ann.put("image_id", imageId);
        ann.put("category_id", cocoClassIndexOf(object, classIndex, categories));
        ann.put("bbox", List.of(NuscenesMetaSupport.round4(bbox[0]), NuscenesMetaSupport.round4(bbox[1]),
                NuscenesMetaSupport.round4(bbox[2]), NuscenesMetaSupport.round4(bbox[3])));
        ann.put("area", NuscenesMetaSupport.round4(bbox[2] * bbox[3]));
        ann.put("iscrowd", 0);
        ann.put("segmentation", segmentation);
        return ann;
    }

    /**
     * COCO 类别 id：按出现顺序从 0 连续编号（与样本包一致）。
     * 平台 classId 是数据库主键、数值大且不连续，直接写进 annotations 会错位；
     * 导入侧按 categories[].id 建索引，对应关系以本文件为准。
     */
    private int cocoClassIndexOf(ExportAnnotationVO object, Map<Long, Integer> classIndex,
                                 List<Map<String, Object>> categories) {
        Integer index = classIndex.get(object.getClassId());
        if (index != null) {
            return index;
        }
        index = categories.size();
        classIndex.put(object.getClassId(), index);
        Map<String, Object> category = new LinkedHashMap<>();
        category.put("id", index);
        category.put("name", classNameOf(object));
        category.put("supercategory", classNameOf(object));
        categories.add(category);
        return index;
    }

    // ---------------- nuScenes 多批次合并导出（单一数据集） ----------------

    /**
     * nuScenes 多批次合并导出：将多个批次合并为「单一 nuScenes 数据集」。
     * <p>布局：数据文件归一到包根（samples/ sweeps/ maps/ lidarseg/），元数据 v1.0-* 表跨批次按 token 合并去重，
     * sample_annotation 统一重写，2D 标注合并为根 COCO，整包一份 readme。兼容单条目的既有单批次语义。</p>
     */
    private void writeNuscenesZipUnified(ZipOutputStream zip, ExportManifestVO manifest) throws IOException {
        NuscenesExportContext context = buildNuscenesContext(manifest);
        String versionDir = resolveUnifiedVersionDir(context);

        // 1. 数据文件：直接落包根（保留 samples/ sweeps/ maps/ 目录层级），按原始相对路径去顶层目录
        Set<String> writtenEntries = new HashSet<>();
        for (ExportAssetVO asset : manifest.getAssets()) {
            String rel = stripTopDir(originalRelativePath(asset));
            if (rel.isEmpty() || !writtenEntries.add(rel)) {
                continue;
            }
            copyAssetFile(zip, rel, asset.getFileUrl());
        }
        for (Long uploadId : context.metaZipByUpload.keySet()) {
            copyOriginalDataFilesFromSourceZip(zip, uploadId, context, "", writtenEntries);
        }

        // 2. 合并元数据表（除重写表）到 <versionDir>/，按 token 去重
        writeMergedMetaTables(zip, context, versionDir);
        writeUnifiedSampleData(zip, context, versionDir, writtenEntries);

        // 3. 统一重写 sample_annotation / attribute / category / instance 关联表
        List<Map<String, Object>> unifiedRows = writeUnifiedSampleAnnotation(zip, manifest, context, versionDir);
        writeUnifiedAttribute(zip, context, versionDir);
        writeUnifiedCategory(zip, context, versionDir);
        writeUnifiedInstance(zip, unifiedRows, context, versionDir);

        // 4. 统一 2D 图片标注 COCO（根 coco_annotations.json）
        writeCocoAnnotationsForUpload(zip, "", manifest.getAssets(), manifest.getAnnotations());

        // 5. 统一点云实例分割（根 lidarseg/）
        writePointCloudSegmentationForUpload(zip, "", context, allAssetIds(manifest), manifest);

        // 6. 平台扩展属性（帧级全局属性 / 无效帧原因 / 批注）
        writePlatformAttributesSidecar(zip, manifest, context);

        // 7. 说明文件（整包一份，放根）
        int skipped = countUnmappedCuboids(manifest, context);
        writeTextEntry(zip, README_FILE_NAME, buildReadme(FORMAT_NUSCENES, manifest, skipped));
    }

    /** 解析多批次合并导出的统一版本目录：取首个非空版本目录，批次间不一致时告警并统一采用首个。 */
    private String resolveUnifiedVersionDir(NuscenesExportContext context) {
        String first = null;
        for (NuscenesMetaBundle bundle : context.bundleByUpload.values()) {
            if (bundle.getVersionDir() != null && !bundle.getVersionDir().isBlank()) {
                if (first == null) {
                    first = bundle.getVersionDir();
                } else if (!first.equals(bundle.getVersionDir())) {
                    log.warn("nuScenes 多批次合并导出：批次间版本目录不一致（{} 与 {}），统一采用 {}",
                            first, bundle.getVersionDir(), first);
                }
            }
        }
        return first != null ? first : (NuscenesMetaSupport.VERSION_DIR_PREFIX + "export");
    }

    /**
     * 跨批次合并 nuScenes 元数据表（sample_annotation 除外，由重写步骤生成）到 <versionDir>/<表名>.json。
     * <p>逐上传读取原始元数据归档（含 scene/log/map 等完整表），按 token 去重后写出；同 token 首条胜出。</p>
     */
    private void writeMergedMetaTables(ZipOutputStream zip, NuscenesExportContext context, String versionDir)
            throws IOException {
        // 表名 -> (token -> 行) 合并去重
        Map<String, Map<String, Map<String, Object>>> merged = new LinkedHashMap<>();
        for (String metaObjectKey : context.metaZipByUpload.values()) {
            if (metaObjectKey == null || metaObjectKey.isBlank()) {
                continue;
            }
            try (InputStream in = minioService.openObject(metaObjectKey);
                 ZipInputStream metaZip = new ZipInputStream(in)) {
                ZipEntry metaEntry;
                while ((metaEntry = metaZip.getNextEntry()) != null) {
                    if (metaEntry.isDirectory()) {
                        continue;
                    }
                    String key = NuscenesMetaSupport.jsonKeyOf(metaEntry.getName());
                    if (key.isEmpty() || NuscenesMetaSupport.TABLE_SAMPLE_ANNOTATION.equals(key)
                        || TABLE_ATTRIBUTE.equals(key) || TABLE_CATEGORY.equals(key)
                        || TABLE_INSTANCE.equals(key) || TABLE_SAMPLE_DATA.equals(key)) {
                        continue;
                    }
                    List<Map<String, Object>> rows = readJsonArrayFromEntry(metaZip);
                    if (rows.isEmpty()) {
                        continue;
                    }
                    Map<String, Map<String, Object>> table = merged.computeIfAbsent(key, k -> new LinkedHashMap<>());
                    for (Map<String, Object> row : rows) {
                        String token = NuscenesMetaSupport.text(row.get("token"));
                        if (!token.isBlank()) {
                            table.putIfAbsent(token, row);
                        } else {
                            table.putIfAbsent("__notoken_" + table.size(), row);
                        }
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("读取 nuScenes 元数据归档失败：" + e.getMessage(), e);
            }
        }
        for (Map.Entry<String, Map<String, Map<String, Object>>> tableEntry : merged.entrySet()) {
            writeJsonEntry(zip, versionDir + "/" + tableEntry.getKey() + ".json",
                    new ArrayList<>(tableEntry.getValue().values()));
        }
    }

    /** 从当前 zip 条目（已定位到文件条目）读取 JSON 数组为行列表。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readJsonArrayFromEntry(ZipInputStream metaZip) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[COPY_BUFFER_SIZE];
        int n;
        while ((n = metaZip.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        Object parsed = parseJson(bos.toString(StandardCharsets.UTF_8));
        if (!(parsed instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                rows.add((Map<String, Object>) map);
            }
        }
        return rows;
    }

    /** 统一生成 sample_annotation.json：合并所有上传的原始（未覆盖）样本与平台标注逆变换重写行。 */
    private List<Map<String, Object>> writeUnifiedSampleAnnotation(ZipOutputStream zip, ExportManifestVO manifest,
            NuscenesExportContext context, String versionDir) throws IOException {
        List<Map<String, Object>> rows = buildUnifiedSampleAnnotations(manifest, context);
        writeJsonEntry(zip, versionDir + "/" + NuscenesMetaSupport.TABLE_SAMPLE_ANNOTATION + ".json", rows);
        return rows;
    }

    /** 单条目导出：重写 attribute.json，加入平台实例属性补充的字典行。 */
    private void writeAttributeJsonForUpload(ZipOutputStream zip, Long uploadId,
                                             NuscenesExportContext context, String folder) throws IOException {
        Map<String, Map<String, Object>> rows = context.attributeRowsByUpload.get(uploadId);
        if (rows == null || rows.isEmpty()) {
            return;
        }
        String dir = context.attributeDirByUpload.get(uploadId);
        if (dir == null || dir.isBlank()) {
            dir = stripTopDir(versionDirOfUpload(context, uploadId));
        }
        String entry = dir.isBlank()
            ? folder + "/" + TABLE_ATTRIBUTE + ".json"
            : folder + "/" + dir + "/" + TABLE_ATTRIBUTE + ".json";
        writeJsonEntry(zip, entry, new ArrayList<>(rows.values()));
    }

    /** 统一导出：跨上传数据合并 attribute 字典后重写。 */
    private void writeUnifiedAttribute(ZipOutputStream zip, NuscenesExportContext context,
                                       String versionDir) throws IOException {
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        for (Map<String, Map<String, Object>> rows : context.attributeRowsByUpload.values()) {
            for (Map.Entry<String, Map<String, Object>> entry : rows.entrySet()) {
                merged.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        if (merged.isEmpty()) {
            return;
        }
        writeJsonEntry(zip, versionDir + "/" + TABLE_ATTRIBUTE + ".json", new ArrayList<>(merged.values()));
    }

    /** 单条目导出：重写 category.json，加入平台自定义类别定义。 */
    private void writeCategoryJsonForUpload(ZipOutputStream zip, Long uploadId,
                                             NuscenesExportContext context, String folder) throws IOException {
        Map<String, Map<String, Object>> rows = context.categoryRowsByUpload.get(uploadId);
        if (rows == null || rows.isEmpty()) {
            return;
        }
        String dir = context.categoryDirByUpload.get(uploadId);
        if (dir == null || dir.isBlank()) {
            dir = stripTopDir(versionDirOfUpload(context, uploadId));
        }
        String entry = dir.isBlank()
            ? folder + "/" + TABLE_CATEGORY + ".json"
            : folder + "/" + dir + "/" + TABLE_CATEGORY + ".json";
        writeJsonEntry(zip, entry, new ArrayList<>(rows.values()));
    }

    /** 统一导出：跨上传数据合并 category 字典后重写。 */
    private void writeUnifiedCategory(ZipOutputStream zip, NuscenesExportContext context,
                                      String versionDir) throws IOException {
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        for (Map<String, Map<String, Object>> rows : context.categoryRowsByUpload.values()) {
            for (Map.Entry<String, Map<String, Object>> entry : rows.entrySet()) {
                merged.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        if (merged.isEmpty()) {
            return;
        }
        writeJsonEntry(zip, versionDir + "/" + TABLE_CATEGORY + ".json", new ArrayList<>(merged.values()));
    }

    /** 按最终 sample_annotation 行重建 instance.json，修复 nbr/first/last 与标注不一致。 */
    private List<Map<String, Object>> buildRewrittenInstanceRows(List<Map<String, Object>> annotations,
                                                                 NuscenesExportContext context) {
        Map<String, List<Map<String, Object>>> byInstance = new LinkedHashMap<>();
        for (Map<String, Object> row : annotations) {
            String token = NuscenesMetaSupport.text(row.get("token"));
            String instanceToken = NuscenesMetaSupport.text(row.get("instance_token"));
            if (token.isBlank() || instanceToken.isBlank()) {
                continue;
            }
            byInstance.computeIfAbsent(instanceToken, k -> new ArrayList<>()).add(row);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : byInstance.entrySet()) {
            List<Map<String, Object>> chain = entry.getValue();
            Map<String, Map<String, Object>> chainByToken = new LinkedHashMap<>();
            for (Map<String, Object> row : chain) {
                String token = NuscenesMetaSupport.text(row.get("token"));
                chainByToken.putIfAbsent(token, row);
            }
            String categoryToken = "";
            String first = "";
            String last = "";
            for (Map<String, Object> row : chain) {
                String token = NuscenesMetaSupport.text(row.get("token"));
                if (categoryToken.isBlank()) {
                    categoryToken = NuscenesMetaSupport.text(row.get("category_token"));
                }
                // 链头：prev 为空或 prev 不在本实例链中
                String prev = NuscenesMetaSupport.text(row.get("prev"));
                if (first.isBlank() && (prev.isBlank() || !chainByToken.containsKey(prev))) {
                    first = token;
                }
            }
            // 链尾：从链头沿 next 走到尽头，不能依赖 rows 的遍历顺序（否则会取到任意中间帧）
            if (!first.isBlank()) {
                Set<String> visited = new HashSet<>();
                String cursor = first;
                while (!cursor.isBlank() && chainByToken.containsKey(cursor) && visited.add(cursor)) {
                    last = cursor;
                    cursor = NuscenesMetaSupport.text(chainByToken.get(cursor).get("next"));
                }
            }
            if (first.isBlank() && !chain.isEmpty()) {
                first = NuscenesMetaSupport.text(chain.get(0).get("token"));
            }
            if (last.isBlank() && !chain.isEmpty()) {
                last = NuscenesMetaSupport.text(chain.get(chain.size() - 1).get("token"));
            }
            if (categoryToken.isBlank()) {
                for (NuscenesIndex index : context.indexByUpload.values()) {
                    Map<String, Object> original = index.instanceByToken.get(entry.getKey());
                    if (original != null) {
                        categoryToken = NuscenesMetaSupport.text(original.get("category_token"));
                        if (!categoryToken.isBlank()) {
                            break;
                        }
                    }
                }
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("token", entry.getKey());
            row.put("category_token", categoryToken);
            row.put("nbr_annotations", chain.size());
            row.put("first_annotation_token", first);
            row.put("last_annotation_token", last);
            result.add(row);
        }
        return result;
    }

    /** 单条目导出：重写 instance.json。 */
    private void writeInstanceJsonForUpload(ZipOutputStream zip, Long uploadId,
                                            NuscenesExportContext context,
                                            List<Map<String, Object>> annotations,
                                            String folder) throws IOException {
        List<Map<String, Object>> rows = buildRewrittenInstanceRows(annotations, context);
        String dir = context.instanceDirByUpload.get(uploadId);
        if (dir == null || dir.isBlank()) {
            dir = stripTopDir(versionDirOfUpload(context, uploadId));
        }
        String entry = dir.isBlank()
            ? folder + "/" + TABLE_INSTANCE + ".json"
            : folder + "/" + dir + "/" + TABLE_INSTANCE + ".json";
        writeJsonEntry(zip, entry, rows);
    }

    /** 统一导出：重写 instance.json。 */
    private void writeUnifiedInstance(ZipOutputStream zip, List<Map<String, Object>> annotations,
                                      NuscenesExportContext context, String versionDir) throws IOException {
        writeJsonEntry(zip, versionDir + "/" + TABLE_INSTANCE + ".json",
            buildRewrittenInstanceRows(annotations, context));
    }

    /** 仅保留实际写入导出包的数据文件对应的 sample_data 行，避免 RADAR/sweep 文件缺失时悬空引用。 */
    private List<Map<String, Object>> filteredSampleDataRows(Long uploadId, NuscenesExportContext context,
                                                             String folderPrefix,
                                                             Set<String> writtenEntries) {
        NuscenesMetaBundle bundle = context.bundleByUpload.get(uploadId);
        if (bundle == null) {
            return List.of();
        }
        String prefix = folderPrefix == null ? "" : folderPrefix.trim();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : NuscenesMetaSupport.listOf(bundle.getTables(), TABLE_SAMPLE_DATA)) {
            String rel = sampleDataRelativePath(NuscenesMetaSupport.text(row.get("filename")));
            if (rel.isBlank()) {
                continue;
            }
            String entry = prefix.isEmpty() ? rel : prefix + "/" + rel;
            if (writtenEntries.contains(entry)) {
                // 构建索引时会给点云行原地注入 _sampleToken/_fileKey，写出前剔除平台内部字段，
                // 保证 sample_data.json 与 nuScenes 官方字段集合完全一致
                Map<String, Object> clean = new LinkedHashMap<>();
                for (Map.Entry<String, Object> cell : row.entrySet()) {
                    String key = cell.getKey();
                    if (key != null && !key.startsWith("_")) {
                        clean.put(key, cell.getValue());
                    }
                }
                result.add(clean);
            }
        }
        return result;
    }

    /** 单条目导出：重写过滤后的 sample_data.json。 */
    private void writeSampleDataJsonForUpload(ZipOutputStream zip, Long uploadId,
                                              NuscenesExportContext context, String folder,
                                              Set<String> writtenEntries) throws IOException {
        List<Map<String, Object>> rows = filteredSampleDataRows(uploadId, context, folder, writtenEntries);
        String dir = context.sampleDataDirByUpload.get(uploadId);
        if (dir == null || dir.isBlank()) {
            dir = stripTopDir(versionDirOfUpload(context, uploadId));
        }
        String entry = dir.isBlank()
            ? folder + "/" + TABLE_SAMPLE_DATA + ".json"
            : folder + "/" + dir + "/" + TABLE_SAMPLE_DATA + ".json";
        writeJsonEntry(zip, entry, rows);
    }

    /** 统一导出：多上传数据合并并过滤 sample_data.json。 */
    private void writeUnifiedSampleData(ZipOutputStream zip, NuscenesExportContext context,
                                        String versionDir, Set<String> writtenEntries) throws IOException {
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        for (Long uploadId : context.bundleByUpload.keySet()) {
            for (Map<String, Object> row : filteredSampleDataRows(uploadId, context, "", writtenEntries)) {
                String token = NuscenesMetaSupport.text(row.get("token"));
                if (!token.isBlank()) {
                    merged.putIfAbsent(token, row);
                }
            }
        }
        writeJsonEntry(zip, versionDir + "/" + TABLE_SAMPLE_DATA + ".json", new ArrayList<>(merged.values()));
    }

    /** 导出平台扩展属性：帧级全局属性、帧有效性/无效原因、整图批注；不写入 nuScenes 标准表。 */
    private void writePlatformAttributesSidecar(ZipOutputStream zip, ExportManifestVO manifest,
                                                NuscenesExportContext context) throws IOException {
        Map<Long, ExportAssetVO> frames = new LinkedHashMap<>();
        for (ExportAssetVO asset : manifest.getAssets()) {
            if (asset.getAssetId() != null) {
                frames.put(asset.getAssetId(), asset);
            }
        }
        // 无效帧被合格数据查询过滤，这里单独补一轮帧状态查询，但只进 sidecar，不进入标注主体。
        for (ExportAssetVO meta : exportMapper.selectExportFrameMeta(manifest.getProjectId(),
            manifest.getBatchId(), manifest.getBatchIds())) {
            if (meta.getAssetId() != null) {
                frames.putIfAbsent(meta.getAssetId(), meta);
            }
        }
        if (frames.isEmpty()) {
            return;
        }
        List<Long> assetIds = new ArrayList<>(frames.keySet());
        Map<Long, Map<String, Object>> annotationMetaByAsset = new LinkedHashMap<>();
        for (int from = 0; from < assetIds.size(); from += ATTRIBUTE_QUERY_CHUNK_SIZE) {
            int to = Math.min(from + ATTRIBUTE_QUERY_CHUNK_SIZE, assetIds.size());
            for (Map<String, Object> row : annotationMapper.selectFrameAnnotationMeta(assetIds.subList(from, to))) {
                Long assetId = asLong(row.get("assetId"));
                if (assetId != null) {
                    annotationMetaByAsset.putIfAbsent(assetId, row);
                }
            }
        }
        List<Map<String, Object>> records = new ArrayList<>();
        for (ExportAssetVO asset : frames.values()) {
            Map<String, Object> meta = annotationMetaByAsset.get(asset.getAssetId());
            LabelNuscenesAssetMap map = context.mapByAsset.get(asset.getAssetId());
            SampleDataRef ref = map == null
                ? context.sampleDataRefByUploadFileName.get(sampleDataRefKey(asset.getUploadId(), asset.getFileName()))
                : null;
            Object global = meta == null ? null : parseJson(meta.get("globalAttrs"));
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("assetId", asset.getAssetId());
            record.put("uploadId", asset.getUploadId());
            record.put("fileName", asset.getFileName());
            record.put("sampleToken", map != null ? stringValue(map.getSampleToken())
                : ref == null ? "" : ref.sampleToken);
            record.put("sampleDataToken", map != null ? stringValue(map.getSampleDataToken())
                : ref == null ? "" : ref.sampleDataToken);
            record.put("globalAttrs", global instanceof Map<?, ?> attrs ? new LinkedHashMap<>(castMap(attrs)) : Map.of());
            record.put("frameFlag", asset.getFrameFlag() == null || asset.getFrameFlag().isBlank()
                ? "normal" : asset.getFrameFlag());
            record.put("invalidReason", stringValue(asset.getInvalidReason()));
            record.put("comment", meta == null ? "" : stringValue(meta.get("comment")));
            records.add(record);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("version", "platform-extension-1.0");
        payload.put("description", "平台扩展：帧级全局属性、帧有效性、无效原因与批注；非 nuScenes 官方表");
        payload.put("records", records);
        writeJsonEntry(zip, PLATFORM_ATTRIBUTES_DIR + "/" + GLOBAL_ATTRS_FILE, payload);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    /**
     * 合并多批次的 sample_annotation：覆盖样本（有点云映射的 sample_token）用平台标注逆变换重写，
     * 其余样本原样保留；跨批次同名 token 重写优先、原样其次。
     */
    private List<Map<String, Object>> buildUnifiedSampleAnnotations(ExportManifestVO manifest,
            NuscenesExportContext context) {
        Map<String, Map<String, Object>> rewrittenByToken = new LinkedHashMap<>();
        Map<String, Map<String, Object>> originalByToken = new LinkedHashMap<>();

        // 平台 cuboid 按资产分组
        Map<Long, List<ExportAnnotationVO>> cuboidsByAsset = new LinkedHashMap<>();
        for (ExportAnnotationVO object : manifest.getAnnotations()) {
            if (!KIND_CUBOID.equals(stringValue(nestedKindValue(object.getGeometry())))) {
                continue;
            }
            cuboidsByAsset.computeIfAbsent(object.getAssetId(), k -> new ArrayList<>()).add(object);
        }
        // 覆盖样本：仅统计确实有平台 cuboid 的帧；无平台框的帧保留原始 sample_annotation
        Set<String> coveredSamples = coveredSampleTokens(cuboidsByAsset, context);

        // 1. 各上传原始（未覆盖）样本行
        for (NuscenesIndex index : context.indexByUpload.values()) {
            for (Map.Entry<String, List<Map<String, Object>>> sampleEntry : index.annotationsBySample.entrySet()) {
                if (coveredSamples.contains(sampleEntry.getKey())) {
                    continue;
                }
                for (Map<String, Object> row : sampleEntry.getValue()) {
                    String token = NuscenesMetaSupport.text(row.get("token"));
                    if (!token.isBlank()) {
                        originalByToken.putIfAbsent(token, row);
                    }
                }
            }
        }
        // 2. 覆盖样本的平台标注逆变换行（先重建实例链：预标注 + 人工延续归并为同一实例）
        Map<String, String[]> chain = resolveNuscenesInstanceChain(manifest, context, allAssetIds(manifest));
        for (Map.Entry<Long, List<ExportAnnotationVO>> assetEntry : cuboidsByAsset.entrySet()) {
            LabelNuscenesAssetMap map = context.mapByAsset.get(assetEntry.getKey());
            if (map == null) {
                continue;
            }
            NuscenesIndex idx = context.indexByUpload.get(map.getUploadId());
            if (idx == null) {
                continue;
            }
            for (ExportAnnotationVO object : assetEntry.getValue()) {
                Map<String, Object> row = toSampleAnnotationRow(object, map, idx, chain, context);
                if (row == null) {
                    continue;
                }
                String token = NuscenesMetaSupport.text(row.get("token"));
                if (!token.isBlank()) {
                    rewrittenByToken.put(token, row);
                }
            }
        }
        // 3. 合并：重写优先，原样其次
        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> done = new HashSet<>();
        for (Map<String, Object> row : rewrittenByToken.values()) {
            String token = NuscenesMetaSupport.text(row.get("token"));
            if (done.add(token)) {
                rows.add(row);
            }
        }
        for (Map<String, Object> row : originalByToken.values()) {
            String token = NuscenesMetaSupport.text(row.get("token"));
            if (token.isBlank() || !done.add(token)) {
                continue;
            }
            rows.add(row);
        }
        relinkAnnotationChains(rows, context);
        return rows;
    }

    /** 收集清单全部资产ID（统一点云分割输出用）。 */
    private Set<Long> allAssetIds(ExportManifestVO manifest) {
        Set<Long> ids = new HashSet<>();
        for (ExportAssetVO asset : manifest.getAssets()) {
            ids.add(asset.getAssetId());
        }
        return ids;
    }

    /** 从任务参数 JSON 解析批次集合（多批次导出下载预览还原范围用）。 */
    @SuppressWarnings("unchecked")
    private List<Long> parseBatchIdsFromParam(String paramJson) {
        if (paramJson == null || paramJson.isBlank()) {
            return List.of();
        }
        Object parsed = parseJson(paramJson);
        if (!(parsed instanceof Map<?, ?> map)) {
            return List.of();
        }
        Object ids = map.get("batchIds");
        if (!(ids instanceof List<?> list)) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Number) {
                result.add(((Number) o).longValue());
            } else if (o instanceof String && !((String) o).isBlank()) {
                try {
                    result.add(Long.parseLong(((String) o).trim()));
                } catch (NumberFormatException ignored) {
                    // 忽略非法 token
                }
            }
        }
        return result;
    }

    /**
     * 构建 nuScenes 导出上下文：按上传数据加载元数据包、索引与点云-相机映射。
     */
    private NuscenesExportContext buildNuscenesContext(ExportManifestVO manifest) {
        NuscenesExportContext context = new NuscenesExportContext();
        // 1. 收集批次内资产涉及的上传数据ID
        Set<Long> uploadIds = new HashSet<>();
        for (ExportAssetVO asset : manifest.getAssets()) {
            if (asset.getUploadId() != null) {
                uploadIds.add(asset.getUploadId());
            }
        }
        // 2. 逐上传数据加载元数据包与索引（无归档的普通包跳过）
        for (Long uploadId : uploadIds) {
            NuscenesMetaBundle bundle = nuscenesImportService.loadBundle(uploadId);
            if (bundle == null || bundle.getTables() == null || bundle.getTables().isEmpty()) {
                continue;
            }
            context.bundleByUpload.put(uploadId, bundle);
            context.indexByUpload.put(uploadId, NuscenesMetaSupport.buildIndex(bundle.getTables()));
            context.metaZipByUpload.put(uploadId, nuscenesImportService.metaOfUpload(uploadId).getMetaObjectKey());
            UploadDataVO upload = projectMapper.selectUploadById(uploadId);
            if (upload != null && upload.getObjectName() != null && !upload.getObjectName().isBlank()) {
                context.sourceZipByUpload.put(uploadId, upload.getObjectName());
            }
            Map<String, Map<String, Object>> attributeRows = new LinkedHashMap<>();
            for (Map<String, Object> row : bundle.getTables().getOrDefault(TABLE_ATTRIBUTE, List.of())) {
                String token = NuscenesMetaSupport.text(row.get("token"));
                if (!token.isBlank()) {
                    attributeRows.putIfAbsent(token, row);
                }
            }
            context.attributeRowsByUpload.put(uploadId, attributeRows);
            Map<String, Map<String, Object>> categoryRows = new LinkedHashMap<>();
            for (Map<String, Object> row : bundle.getTables().getOrDefault(TABLE_CATEGORY, List.of())) {
                String token = NuscenesMetaSupport.text(row.get("token"));
                if (!token.isBlank()) {
                    categoryRows.putIfAbsent(token, row);
                }
            }
            context.categoryRowsByUpload.put(uploadId, categoryRows);
            for (Map<String, Object> row : NuscenesMetaSupport.listOf(bundle.getTables(), "sample_data")) {
                String filename = NuscenesMetaSupport.text(row.get("filename"));
                if (filename.isBlank()) {
                    continue;
                }
                SampleDataRef ref = new SampleDataRef(NuscenesMetaSupport.text(row.get("sample_token")),
                    NuscenesMetaSupport.text(row.get("token")));
                context.sampleDataRefByUploadFileName.putIfAbsent(sampleDataRefKey(uploadId, basenameOf(filename)), ref);
                context.sampleDataRefByUploadFileName.putIfAbsent(sampleDataRefKey(uploadId, filename), ref);
            }
        }
        // 3. 逐上传数据加载点云-相机映射（按资产ID索引，导出逆变换定位 sample/标定用）
        for (Long uploadId : context.bundleByUpload.keySet()) {
            for (LabelNuscenesAssetMap map : nuscenesMapper.selectMapsByUploadId(uploadId)) {
                context.mapByAsset.put(map.getAssetId(), map);
            }
        }
        return context;
    }

    /**
     * 从某条目的 MinIO 元数据归档原样复制 v1.0-* JSON（字节级还原；sample_annotation.json 由重写步骤生成）。
     * 仅处理该条目（uploadId）的归档，写入时加上其文件夹前缀并去掉原包顶层目录，使多个条目互不干扰。
     */
    private void writeNuscenesMetaEntriesForUpload(ZipOutputStream zip, Long uploadId,
                                                  NuscenesExportContext context, Set<String> writtenEntries,
                                                  String folder) throws IOException {
        String metaObjectKey = context.metaZipByUpload.get(uploadId);
        if (metaObjectKey == null || metaObjectKey.isBlank()) {
            return;
        }
        try (InputStream in = minioService.openObject(metaObjectKey);
             ZipInputStream metaZip = new ZipInputStream(in)) {
            ZipEntry metaEntry;
            while ((metaEntry = metaZip.getNextEntry()) != null) {
                if (metaEntry.isDirectory()) {
                    continue;
                }
                String jsonKey = NuscenesMetaSupport.jsonKeyOf(metaEntry.getName());
                // 需要按最终标注重写的表：记录原目录并跳过，后面由重写步骤生成
                if (NuscenesMetaSupport.TABLE_SAMPLE_ANNOTATION.equals(jsonKey)) {
                    context.sampleAnnotationDirByUpload.putIfAbsent(uploadId,
                            parentDirOf(stripTopDir(metaEntry.getName())));
                    continue;
                }
                if (TABLE_ATTRIBUTE.equals(jsonKey)) {
                    context.attributeDirByUpload.putIfAbsent(uploadId,
                            parentDirOf(stripTopDir(metaEntry.getName())));
                    continue;
                }
                if (TABLE_CATEGORY.equals(jsonKey)) {
                    context.categoryDirByUpload.putIfAbsent(uploadId,
                            parentDirOf(stripTopDir(metaEntry.getName())));
                    continue;
                }
                if (TABLE_INSTANCE.equals(jsonKey)) {
                    context.instanceDirByUpload.putIfAbsent(uploadId,
                            parentDirOf(stripTopDir(metaEntry.getName())));
                    continue;
                }
                if (TABLE_SAMPLE_DATA.equals(jsonKey)) {
                    context.sampleDataDirByUpload.putIfAbsent(uploadId,
                            parentDirOf(stripTopDir(metaEntry.getName())));
                    continue;
                }
                String rel = stripTopDir(metaEntry.getName());
                String entryName = folder + "/" + rel;
                if (rel.isEmpty() || !writtenEntries.add(entryName)) {
                    continue;
                }
                zip.putNextEntry(new ZipEntry(entryName));
                byte[] buffer = new byte[COPY_BUFFER_SIZE];
                int len;
                while ((len = metaZip.read(buffer)) > 0) {
                    zip.write(buffer, 0, len);
                }
                zip.closeEntry();
            }
        }
    }

    /** 计算真正被平台 cuboid 改写的 sample_token 集合；未出现平台框的帧应保留原始标注。 */
    private Set<String> coveredSampleTokens(Map<Long, List<ExportAnnotationVO>> cuboidsByAsset,
                                            NuscenesExportContext context) {
        Set<String> samples = new HashSet<>();
        for (Long assetId : cuboidsByAsset.keySet()) {
            LabelNuscenesAssetMap map = context.mapByAsset.get(assetId);
            if (map != null && map.getSampleToken() != null && !map.getSampleToken().isBlank()) {
                samples.add(map.getSampleToken());
            }
        }
        return samples;
    }

    /**
     * 重写某条目的 sample_annotation 行集合（仅该 uploadId 覆盖的样本 + 该条目未覆盖样本原样保留）。
     */
    private List<Map<String, Object>> buildRewrittenSampleAnnotationsForUpload(ExportManifestVO manifest,
            NuscenesExportContext context, Long uploadId, Set<Long> groupAssetIds) {
        List<Map<String, Object>> rows = new ArrayList<>();
        // 1. 平台 cuboid 对象按资产分组，覆盖样本仅统计有平台框的帧
        Map<Long, List<ExportAnnotationVO>> cuboidsByAsset = new LinkedHashMap<>();
        for (ExportAnnotationVO object : manifest.getAnnotations()) {
            if (!KIND_CUBOID.equals(stringValue(nestedKindValue(object.getGeometry())))) {
                continue;
            }
            if (!groupAssetIds.contains(object.getAssetId())) {
                continue;
            }
            cuboidsByAsset.computeIfAbsent(object.getAssetId(), k -> new ArrayList<>()).add(object);
        }
        Set<String> coveredSamples = coveredSampleTokens(cuboidsByAsset, context);
        // 2. 该条目未覆盖样本的原始行原样保留
        NuscenesIndex index = context.indexByUpload.get(uploadId);
        if (index != null) {
            for (Map.Entry<String, List<Map<String, Object>>> sampleEntry : index.annotationsBySample.entrySet()) {
                if (coveredSamples.contains(sampleEntry.getKey())) {
                    continue;
                }
                rows.addAll(sampleEntry.getValue());
            }
        }
        // 3. 覆盖样本的平台标注逆变换生成行（先重建实例链：预标注 + 人工延续归并为同一实例）
        Map<String, String[]> chain = resolveNuscenesInstanceChain(manifest, context, groupAssetIds);
        for (Map.Entry<Long, List<ExportAnnotationVO>> assetEntry : cuboidsByAsset.entrySet()) {
            LabelNuscenesAssetMap map = context.mapByAsset.get(assetEntry.getKey());
            if (map == null) {
                continue;
            }
            NuscenesIndex idx = context.indexByUpload.get(map.getUploadId());
            if (idx == null) {
                continue;
            }
            for (ExportAnnotationVO object : assetEntry.getValue()) {
                Map<String, Object> row = toSampleAnnotationRow(object, map, idx, chain, context);
                if (row != null) {
                    rows.add(row);
                }
            }
        }
        relinkAnnotationChains(rows, context);
        return rows;
    }

    /** 按最终标注集合重建各实例的 prev/next 链，消除覆盖帧替换后遗留的悬空引用。 */
    private void relinkAnnotationChains(List<Map<String, Object>> rows, NuscenesExportContext context) {
        Map<String, List<Map<String, Object>>> byInstance = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String instanceToken = NuscenesMetaSupport.text(row.get("instance_token"));
            if (!instanceToken.isBlank()) {
                byInstance.computeIfAbsent(instanceToken, k -> new ArrayList<>()).add(row);
            }
        }
        for (List<Map<String, Object>> chain : byInstance.values()) {
            chain.sort((a, b) -> {
                int fa = frameOrder(context, NuscenesMetaSupport.text(a.get("sample_token")));
                int fb = frameOrder(context, NuscenesMetaSupport.text(b.get("sample_token")));
                if (fa != fb) {
                    return Integer.compare(fa, fb);
                }
                return NuscenesMetaSupport.text(a.get("token"))
                    .compareTo(NuscenesMetaSupport.text(b.get("token")));
            });
            for (int i = 0; i < chain.size(); i++) {
                Map<String, Object> row = chain.get(i);
                row.put("prev", i == 0 ? "" : NuscenesMetaSupport.text(chain.get(i - 1).get("token")));
                row.put("next", i == chain.size() - 1 ? ""
                    : NuscenesMetaSupport.text(chain.get(i + 1).get("token")));
            }
        }
    }

    /** 取 sample 在所属 scene 中的帧序，跨上传查找；无帧序信息时排到最后。 */
    private int frameOrder(NuscenesExportContext context, String sampleToken) {
        if (sampleToken.isBlank()) {
            return Integer.MAX_VALUE;
        }
        for (NuscenesIndex index : context.indexByUpload.values()) {
            int order = NuscenesMetaSupport.frameIndexOf(index, sampleToken);
            if (order > 0) {
                return order;
            }
        }
        return Integer.MAX_VALUE;
    }

    /** 解析实例属性 token：预标注沿用原 token，人工属性按 attribute 字典映射或补充字典后生成。 */
    private List<String> resolveAttributeTokens(ExportAnnotationVO object, Map<String, Object> attributes,
                                                NuscenesIndex index, NuscenesExportContext context, Long uploadId,
                                                Map<String, Object> originalRow) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (String token : stringValues(attributes.get("attributeTokens"))) {
            if (index.attributeNameByToken.containsKey(token)) {
                tokens.add(token);
            }
        }
        for (String name : stringValues(attributes.get("attributeNames"))) {
            addAttributeByName(tokens, name, index, context, uploadId);
        }
        String motion = stringValue(attributes.get("motionStatus"));
        if (!motion.isBlank()) {
            addAttributeByName(tokens, "vehicle." + motion.toLowerCase(Locale.ROOT), index, context, uploadId);
        }
        String className = stringValue(object.getClassName()).toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String key = stringValue(entry.getKey());
            if (key.isBlank() || INTERNAL_ATTRIBUTE_KEYS.contains(key) || "motionStatus".equals(key)) {
                continue;
            }
            Object raw = entry.getValue();
            // 数组/对象字段是几何、点索引或元数据，不是标量实例属性，不能生成 attribute token
            if (raw == null || raw instanceof Collection<?> || raw instanceof Map<?, ?>
                || (raw instanceof Boolean flag && !flag)) {
                continue;
            }
            String value = stringValue(raw);
            if (value.isBlank()) {
                continue;
            }
            List<String> candidates = new ArrayList<>();
            if (raw instanceof Boolean) {
                candidates.add(key);
            } else {
                candidates.add(value);
                candidates.add(key + "." + value);
                candidates.add(className + "." + value);
                candidates.add("vehicle." + value);
            }
            // 仅接受能对应到 attribute 字典已有属性名的标量字段；其余是平台元数据
            // （number / status / isKeyFrame 等），不能自动注册成自定义属性
            for (String candidate : candidates) {
                String known = findAttributeTokenByName(index, candidate);
                if (known != null) {
                    tokens.add(known);
                    break;
                }
            }
        }
        // 预标注来源的框平台未保存实例属性时，沿用原包该标注的合法属性，避免属性整段丢失
        if (tokens.isEmpty() && originalRow != null) {
            for (String token : stringValues(originalRow.get("attribute_tokens"))) {
                if (index.attributeNameByToken.containsKey(token)) {
                    tokens.add(token);
                }
            }
        }
        return new ArrayList<>(tokens);
    }

    /** 将属性名解析为字典 token；字典不存在时补充一条稳定的自定义属性定义。 */
    private void addAttributeByName(LinkedHashSet<String> tokens, String name, NuscenesIndex index,
                                    NuscenesExportContext context, Long uploadId) {
        String candidate = stringValue(name).trim();
        if (candidate.isBlank()) {
            return;
        }
        String known = findAttributeTokenByName(index, candidate);
        if (known != null) {
            tokens.add(known);
            return;
        }
        tokens.add(registerCustomAttribute(context, uploadId, candidate));
    }

    /** 在索引中按属性名（忽略大小写）反查 token。 */
    private String findAttributeTokenByName(NuscenesIndex index, String name) {
        String target = stringValue(name).trim().toLowerCase(Locale.ROOT);
        if (target.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, String> entry : index.attributeNameByToken.entrySet()) {
            if (target.equals(stringValue(entry.getValue()).trim().toLowerCase(Locale.ROOT))) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** 注册平台自定义属性到该上传数据的 attribute 字典，token 由属性名确定性生成。 */
    private String registerCustomAttribute(NuscenesExportContext context, Long uploadId, String name) {
        Map<String, Map<String, Object>> rows = context.attributeRowsByUpload
            .computeIfAbsent(uploadId, k -> new LinkedHashMap<>());
        String normalized = stringValue(name).trim();
        for (Map.Entry<String, Map<String, Object>> entry : rows.entrySet()) {
            if (normalized.equalsIgnoreCase(stringValue(entry.getValue().get("name")))) {
                return entry.getKey();
            }
        }
        String token = stableToken("nuScenes-attribute:" + normalized.toLowerCase(Locale.ROOT));
        int suffix = 1;
        while (rows.containsKey(token)) {
            token = stableToken("nuScenes-attribute:" + normalized.toLowerCase(Locale.ROOT) + ":" + suffix++);
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("token", token);
        row.put("name", normalized);
        row.put("description", "平台实例属性导出：" + normalized);
        rows.put(token, row);
        return token;
    }

    /** 属性值既可能是数组，也可能是逗号分隔字符串（兼容历史存档）。 */
    private List<String> stringValues(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                String text = stringValue(item).trim();
                if (!text.isEmpty()) {
                    result.add(text);
                }
            }
            return result;
        }
        String text = stringValue(value).trim();
        if (text.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String part : text.split(",")) {
            String item = part.trim();
            if (!item.isEmpty()) {
                result.add(item);
            }
        }
        return result;
    }

    /** 解析类别 token：预标注沿用原 token；人工类别按名称反查或补充 category 字典。 */
    private String resolveCategoryToken(ExportAnnotationVO object, Map<String, Object> attributes,
                                        NuscenesIndex index, NuscenesExportContext context, Long uploadId) {
        String preferredToken = stringValue(attributes.get("categoryToken")).trim();
        String categoryName = stringValue(attributes.get("categoryName")).trim();
        if (categoryName.isBlank()) {
            categoryName = stringValue(object.getClassName()).trim();
        }
        if (categoryName.isBlank()) {
            categoryName = CATEGORY_FALLBACK_NAME;
        }
        if (index != null) {
            if (!preferredToken.isBlank() && index.categoryNameByToken.containsKey(preferredToken)) {
                return preferredToken;
            }
            String byName = index.categoryTokenByName.get(categoryName.toLowerCase(Locale.ROOT));
            if (byName != null && !byName.isBlank()) {
                return byName;
            }
        }
        return registerCustomCategory(context, uploadId, categoryName, preferredToken);
    }

    /** 注册平台自定义类别到 category.json，保证 sample_annotation.category_token 有字典项。 */
    private String registerCustomCategory(NuscenesExportContext context, Long uploadId,
                                          String name, String preferredToken) {
        Map<String, Map<String, Object>> rows = context.categoryRowsByUpload
            .computeIfAbsent(uploadId, k -> new LinkedHashMap<>());
        String normalized = stringValue(name).trim();
        if (normalized.isEmpty()) {
            normalized = CATEGORY_FALLBACK_NAME;
        }
        for (Map.Entry<String, Map<String, Object>> entry : rows.entrySet()) {
            if (normalized.equalsIgnoreCase(stringValue(entry.getValue().get("name")))) {
                return entry.getKey();
            }
        }
        String token = preferredToken.trim();
        if (token.length() != NUSCENES_TOKEN_HEX_LENGTH || !token.matches("[0-9a-fA-F]+")) {
            token = stableToken("nuScenes-category:" + normalized.toLowerCase(Locale.ROOT));
        }
        int suffix = 1;
        while (rows.containsKey(token)) {
            token = stableToken("nuScenes-category:" + normalized.toLowerCase(Locale.ROOT) + ":" + suffix++);
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("token", token);
        row.put("name", normalized);
        row.put("description", "平台类别导出：" + normalized);
        rows.put(token, row);
        return token;
    }

    /**
     * 平台 cuboid -&gt; nuScenes sample_annotation 行（sensor -&gt; ego -&gt; global 逆变换）。
     * <p>预标注来源的框 objectKey 即原始 annotation token、attr 携带 instance/category/visibility
     * 与点数字段，导出后与官方数据无缝衔接；人工新画的框自动生成合法 token。</p>
     * <p>chain 为 {@link #resolveNuscenesInstanceChain} 的结果（objectKey -&gt;
     * {annotationToken, instanceToken, prev, next}），非空时 token / instance_token / prev / next
     * 一律以它为准 —— 预标注与人工延续帧由此归并为同一条实例链。</p>
     */
    private Map<String, Object> toSampleAnnotationRow(ExportAnnotationVO object, LabelNuscenesAssetMap map,
                                                       NuscenesIndex index, Map<String, String[]> chain,
                                                       NuscenesExportContext context) {
        // 1. 定位该点云资产的 sample_data / 自车位姿 / 标定（逆变换参数）
        Map<String, Object> sampleData = index.sampleDataByToken.get(map.getSampleDataToken());
        if (sampleData == null) {
            return null;
        }
        Map<String, Object> egoPose = index.egoPoseByToken.get(
            NuscenesMetaSupport.text(sampleData.get("ego_pose_token")));
        Map<String, Object> calib = index.calibratedSensorByToken.get(
            NuscenesMetaSupport.text(sampleData.get("calibrated_sensor_token")));
        if (egoPose == null || calib == null) {
            return null;
        }
        // 2. 解析平台几何（雷达传感器系）
        if (!(object.getGeometry() instanceof Map<?, ?> geometry)
            || !(geometry.get("center") instanceof Map<?, ?> center)
            || !(geometry.get("size") instanceof Map<?, ?> size)) {
            return null;
        }
        double[] centerSensor = {
            asNumber(center.get("x")).doubleValue(),
            asNumber(center.get("y")).doubleValue(),
            asNumber(center.get("z")).doubleValue()};
        double yaw = asNumber(geometry.get("rotationZ")).doubleValue();
        // 3. 逆变换到 global：t_g = t_ego + R_ego·(t_sensor + R_sensor·c)；R_g = R_ego·R_sensor·R_yaw
        double[] egoRotation = NuscenesMetaSupport.quatToMatrix(NuscenesMetaSupport.quat(egoPose.get("rotation")));
        double[] egoTranslation = NuscenesMetaSupport.vec3(egoPose.get("translation"));
        double[] sensorRotation = NuscenesMetaSupport.quatToMatrix(NuscenesMetaSupport.quat(calib.get("rotation")));
        double[] sensorTranslation = NuscenesMetaSupport.vec3(calib.get("translation"));
        double[] centerGlobal = NuscenesMetaSupport.add(egoTranslation, NuscenesMetaSupport.matVec(egoRotation,
            NuscenesMetaSupport.add(sensorTranslation, NuscenesMetaSupport.matVec(sensorRotation, centerSensor))));
        double[] rotation = NuscenesMetaSupport.matMul(NuscenesMetaSupport.matMul(egoRotation, sensorRotation),
            NuscenesMetaSupport.yawToMatrix(yaw));
        double[] quat = NuscenesMetaSupport.matrixToQuat(rotation);
        // 4. 尺寸：平台 [长x, 宽y, 高z] -> nuScenes [宽w, 长l, 高h]
        double length = asNumber(size.get("x")).doubleValue();
        double width = asNumber(size.get("y")).doubleValue();
        double height = asNumber(size.get("z")).doubleValue();
        // 5. 属性回填（预标注来源沿用 token；人工框按平台标签属性映射/补充 attribute 字典）
        Map<String, Object> attributes = object.getAttributes() instanceof Map<?, ?> attr
            ? (Map<String, Object>) attr : Map.of();
        // 实例链（预标注 + 人工延续归并成同一实例）：token/instance_token/prev/next 全部由链解析结果决定，
        // 缺解析结果（对象没进链，理论上不会发生）时退回逐对象独立生成的旧行为
        String[] link = chain != null ? chain.get(object.getObjectKey()) : null;
        String token = link != null ? link[0] : nuscenesToken(object.getObjectKey());
        String instanceToken = link != null ? link[1] : nuscenesToken(stringValue(attributes.get("instanceToken")));
        String categoryToken = resolveCategoryToken(object, attributes, index, context, map.getUploadId());
        // 预标注来源的框沿用原包实例链：覆盖帧后续仍是原始行，此处清空 next 会截断整条轨迹
        Map<String, Object> originalRow = index.annotationByToken.get(token);
        List<String> attributeTokens = resolveAttributeTokens(object, attributes, index, context,
            map.getUploadId(), originalRow);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("token", token);
        row.put("sample_token", map.getSampleToken());
        row.put("instance_token", instanceToken);
        row.put("category_token", categoryToken);
        row.put("attribute_tokens", attributeTokens);
        row.put("visibility_token", stringValue(attributes.get("visibilityToken")));
        row.put("translation", List.of(NuscenesMetaSupport.round4(centerGlobal[0]),
            NuscenesMetaSupport.round4(centerGlobal[1]), NuscenesMetaSupport.round4(centerGlobal[2])));
        row.put("rotation", List.of(NuscenesMetaSupport.round4(quat[0]), NuscenesMetaSupport.round4(quat[1]),
            NuscenesMetaSupport.round4(quat[2]), NuscenesMetaSupport.round4(quat[3])));
        row.put("size", List.of(NuscenesMetaSupport.round4(width), NuscenesMetaSupport.round4(length),
            NuscenesMetaSupport.round4(height)));
        // prev/next：预标注框沿用原包链，人工新框按同实例帧序串成单链，孤立实例为空串
        row.put("prev", originalRow != null ? NuscenesMetaSupport.text(originalRow.get("prev"))
            : (link != null ? link[2] : ""));
        row.put("next", originalRow != null ? NuscenesMetaSupport.text(originalRow.get("next"))
            : (link != null ? link[3] : ""));
        row.put("num_lidar_pts", asInt(attributes.get("numLidarPts")));
        row.put("num_radar_pts", asInt(attributes.get("numRadarPts")));
        return row;
    }

    /** 实例链归组用的节点：一个平台对象的三类引用（instanceToken / trackId / objectKey）与帧序。 */
    private static final class ChainNode {
        final String objectKey;
        final String instanceToken;
        final String trackId;
        final int frameOrder;

        ChainNode(String objectKey, String instanceToken, String trackId, int frameOrder) {
            this.objectKey = objectKey;
            this.instanceToken = instanceToken;
            this.trackId = trackId;
            this.frameOrder = frameOrder;
        }
    }

    /**
     * 重建 nuScenes 实例链：把预标注来源与人工标注来源的框归并成同一批实例。
     *
     * <p>平台标注分两类：预标注框 attributes 携带原始 {@code instanceToken}（跨帧同值）；
     * 人工轨迹框靠 {@code trackId} 关联（关键帧/插值帧同值），且通过选中预标注框广播
     * ghost 再确认落框的延续帧，其 trackId 恰好等于预标注源框的 objectKey（trackKeyOf
     * 对无 trackId 的预标注框兜底用 objectKey）—— 这条隐式桥接就是归并的关键。</p>
     *
     * <p>归组按对象的三类引用做并查集连通：① 相同 instanceToken；② 相同 trackId；
     * ③ trackId 与其它对象的 objectKey 相同（人工延续帧 ← 桥接 → 预标注源帧）。
     * 仅在 scope 资产范围内归组，跨条目互不串扰。</p>
     *
     * <p>instance_token：组内优先用原始 instanceToken（保真 nuScenes 原链），纯人工轨迹用
     * trackId/objectKey 确定性生成（MD5 取 32 位 hex）—— 同轨迹跨帧一致，多次导出结果稳定。
     * annotation token 同样由 objectKey 确定性生成；prev/next 把组内对象按帧序串成单链
     * （nuScenes 实例链语义），组内只有一个对象时为空串。</p>
     *
     * @return objectKey -&gt; {annotationToken, instanceToken, prevToken, nextToken}
     */
    private Map<String, String[]> resolveNuscenesInstanceChain(ExportManifestVO manifest,
            NuscenesExportContext context, Set<Long> scopeAssetIds) {
        List<ChainNode> nodes = new ArrayList<>();
        for (ExportAnnotationVO object : manifest.getAnnotations()) {
            if (!KIND_CUBOID.equals(stringValue(nestedKindValue(object.getGeometry())))) {
                continue;
            }
            if (!scopeAssetIds.contains(object.getAssetId())) {
                continue;
            }
            LabelNuscenesAssetMap map = context.mapByAsset.get(object.getAssetId());
            // 与实际生成行的集合保持一致：没有 nuScenes 帧映射的对象不参与链（也不会出现在导出包里）
            if (map == null || map.getSampleToken() == null || map.getSampleToken().isBlank()) {
                continue;
            }
            Map<String, Object> attrs = object.getAttributes() instanceof Map<?, ?> attr
                ? (Map<String, Object>) attr : Map.of();
            NuscenesIndex idx = context.indexByUpload.get(map.getUploadId());
            int frameOrder = idx != null && map.getSampleToken() != null
                ? idx.frameIndexBySampleToken.getOrDefault(map.getSampleToken(), Integer.MAX_VALUE)
                : Integer.MAX_VALUE;
            String objectKey = stringValue(object.getObjectKey());
            nodes.add(new ChainNode(objectKey,
                stringValue(attrs.get("instanceToken")), stringValue(attrs.get("trackId")), frameOrder));
        }
        // 并查集：引用 = "K:"objectKey / "I:"instanceToken / "T:"trackId；同一对象的引用并成一组
        Map<String, String> parent = new HashMap<>();
        for (ChainNode node : nodes) {
            for (String ref : refsOf(node)) {
                parent.putIfAbsent(ref, ref);
            }
            String[] refs = refsOf(node);
            for (int i = 1; i < refs.length; i++) {
                union(parent, refs[0], refs[i]);
            }
        }
        // 组根 -> 成员
        Map<String, List<ChainNode>> groups = new LinkedHashMap<>();
        for (ChainNode node : nodes) {
            groups.computeIfAbsent(find(parent, "K:" + node.objectKey), k -> new ArrayList<>()).add(node);
        }
        Map<String, String[]> result = new HashMap<>();
        for (List<ChainNode> group : groups.values()) {
            // 帧序排序（无帧序信息的排最后，再按 objectKey 稳定次序）
            List<ChainNode> ordered = new ArrayList<>(group);
            ordered.sort((a, b) -> a.frameOrder != b.frameOrder
                ? Integer.compare(a.frameOrder, b.frameOrder)
                : a.objectKey.compareTo(b.objectKey));
            // instance_token：组内优先原始 instanceToken；否则用组内首个 trackId/objectKey 确定性生成
            String instanceToken = "";
            for (ChainNode node : ordered) {
                if (!node.instanceToken.isBlank()) {
                    instanceToken = validHexToken(node.instanceToken);
                    break;
                }
            }
            if (instanceToken.isBlank()) {
                ChainNode seed = ordered.get(0);
                instanceToken = stableToken(!seed.trackId.isBlank() ? "track:" + seed.trackId : "track:" + seed.objectKey);
            }
            // 按帧序串 prev/next
            String prev = "";
            ChainNode prevNode = null;
            for (ChainNode node : ordered) {
                String annToken = validHexToken(node.objectKey);
                result.put(node.objectKey, new String[]{annToken, instanceToken, prev, ""});
                if (prevNode != null) {
                    result.get(prevNode.objectKey)[3] = annToken;
                }
                prev = annToken;
                prevNode = node;
            }
        }
        return result;
    }

    /** 对象的三类归组引用（并查集节点）。 */
    private String[] refsOf(ChainNode node) {
        List<String> refs = new ArrayList<>(3);
        refs.add("K:" + node.objectKey);
        if (!node.instanceToken.isBlank()) {
            refs.add("I:" + node.instanceToken);
        }
        if (!node.trackId.isBlank()) {
            refs.add("T:" + node.trackId);
        }
        return refs.toArray(new String[0]);
    }

    private String find(Map<String, String> parent, String ref) {
        String root = ref;
        while (!root.equals(parent.get(root))) {
            root = parent.get(root);
        }
        // 路径压缩
        String cur = ref;
        while (!cur.equals(root)) {
            String next = parent.get(cur);
            parent.put(cur, root);
            cur = next;
        }
        return root;
    }

    private void union(Map<String, String> parent, String a, String b) {
        String ra = find(parent, a);
        String rb = find(parent, b);
        if (!ra.equals(rb)) {
            parent.put(ra, rb);
        }
    }

    /** 合法 32 位 hex token 原样小写返回（保真 nuScenes 原值）；否则按 MD5 确定性生成。 */
    private String validHexToken(String candidate) {
        String value = candidate == null ? "" : candidate.trim();
        if (value.length() == NUSCENES_TOKEN_HEX_LENGTH && value.matches("[0-9a-fA-F]+")) {
            return value.toLowerCase(Locale.ROOT);
        }
        return stableToken(value);
    }

    /** 确定性 32 位 hex token（MD5）：同输入同输出，多次导出结果一致。 */
    private String stableToken(String seed) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest((seed == null ? "" : seed).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // JVM 必有 MD5，不会走到；兜底保持合法格式
            return UUID.randomUUID().toString().replace("-", "");
        }
    }

    /**
     * 输出某条目的 2D 图片标注（COCO 格式），仅含该条目图片资产及其标注，写入其文件夹下。
     */
    private void writeCocoAnnotationsForUpload(ZipOutputStream zip, String folder,
            List<ExportAssetVO> groupAssets, List<ExportAnnotationVO> allAnnotations) throws IOException {
        // 1. images（仅该条目图片资产）
        List<Map<String, Object>> images = new ArrayList<>();
        Set<Long> imageAssetIds = new HashSet<>();
        for (ExportAssetVO asset : groupAssets) {
            if (!MediaType.IMAGE.equals(asset.getMediaType())) {
                continue;
            }
            imageAssetIds.add(asset.getAssetId());
            Map<String, Object> image = new LinkedHashMap<>();
            image.put("id", asset.getAssetId());
            image.put("file_name", stripTopDir(originalRelativePath(asset)));
            images.add(image);
        }
        // 2. categories + 3. annotations
        Map<Long, Map<String, Object>> categoryById = new LinkedHashMap<>();
        List<Map<String, Object>> annotations = new ArrayList<>();
        Map<Long, List<ExportAnnotationVO>> objectsByAsset = new LinkedHashMap<>();
        for (ExportAnnotationVO object : allAnnotations) {
            objectsByAsset.computeIfAbsent(object.getAssetId(), k -> new ArrayList<>()).add(object);
        }
        long annotationId = 0L;
        for (Long assetId : imageAssetIds) {
            for (ExportAnnotationVO object : objectsByAsset.getOrDefault(assetId, List.of())) {
                Map<String, Object> coco = toCocoAnnotation(object, ++annotationId);
                if (coco == null) {
                    continue;
                }
                List<?> skeletonEdges = (List<?>) coco.remove("_skeleton_edges");
                registerCocoCategory(categoryById, object.getClassId(), object.getClassName(), skeletonEdges);
                coco.put("category_id", object.getClassId() == null ? 0L : object.getClassId());
                annotations.add(coco);
            }
        }
        if (imageAssetIds.isEmpty() || annotations.isEmpty()) {
            return;
        }
        Map<String, Object> coco = new LinkedHashMap<>();
        coco.put("images", images);
        coco.put("annotations", annotations);
        coco.put("categories", new ArrayList<>(categoryById.values()));
        String cocoEntry = folder == null || folder.isEmpty() ? COCO_FILE_NAME : folder + "/" + COCO_FILE_NAME;
        writeJsonEntry(zip, cocoEntry, coco);
    }

    /**
     * 输出某条目的点云实例分割标注（geometry.kind=point_cloud_instance 的 pointIndices），
     * 按点云资产聚合，写入其文件夹下 lidarseg/{点云 sample_data token}_segmentation.json。
     * <p>仅该条目、且该点云资产有 nuScenes 映射（可定位 sample_data token）时输出；
     * 普通（非 nuScenes）点云资产无映射则跳过。每个文件含该点云全部实例分割
     * （instanceToken/category/classId/pointIndices 点索引列表），格式信息无损，可直连重导入。</p>
     */
    private void writePointCloudSegmentationForUpload(ZipOutputStream zip, String folder,
            NuscenesExportContext context, Set<Long> groupAssetIds, ExportManifestVO manifest) throws IOException {
        // 1. 按点云资产聚合点云分割标注（point_cloud_instance 实例分割 / segmentation 语义分割，均为 pointIndices）
        Map<Long, List<ExportAnnotationVO>> segByAsset = new LinkedHashMap<>();
        for (ExportAnnotationVO object : manifest.getAnnotations()) {
            if (!KIND_POINT_CLOUD_SEGMENTATIONS.contains(stringValue(nestedKindValue(object.getGeometry())))) {
                continue;
            }
            if (!groupAssetIds.contains(object.getAssetId())) {
                continue;
            }
            LabelNuscenesAssetMap map = context.mapByAsset.get(object.getAssetId());
            if (map == null || map.getSampleDataToken() == null || map.getSampleDataToken().isBlank()) {
                continue;
            }
            segByAsset.computeIfAbsent(object.getAssetId(), k -> new ArrayList<>()).add(object);
        }
        if (segByAsset.isEmpty()) {
            return;
        }
        // 2. 逐点云资产输出 lidarseg 文件
        for (Map.Entry<Long, List<ExportAnnotationVO>> en : segByAsset.entrySet()) {
            LabelNuscenesAssetMap map = context.mapByAsset.get(en.getKey());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sampleDataToken", map.getSampleDataToken());
            payload.put("sampleToken", map.getSampleToken());
            payload.put("format", "point_indices");
            List<Map<String, Object>> instances = new ArrayList<>();
            for (ExportAnnotationVO object : en.getValue()) {
                Map<String, Object> inst = new LinkedHashMap<>();
                Map<String, Object> attributes = object.getAttributes() instanceof Map<?, ?> attr
                        ? (Map<String, Object>) attr : Map.of();
                String instanceToken = stringValue(attributes.get("instanceToken")).trim();
                if (instanceToken.isBlank()) {
                    instanceToken = stableToken(LIDARSEG_INSTANCE_SEED + en.getKey() + ":"
                        + stringValue(object.getObjectKey()));
                } else {
                    instanceToken = nuscenesToken(instanceToken);
                }
                inst.put("instanceToken", instanceToken);
                inst.put("category", object.getClassName());
                inst.put("classId", object.getClassId() == null ? 0L : object.getClassId());
                Object points = object.getGeometry() instanceof Map<?, ?> g ? g.get("pointIndices") : null;
                // pointIndices 可能是编码格式 {enc:'drle-b64',...}（前端保存时压缩，
                // 见 PointIndicesCodec）—— 统一还原为明文索引列表再导出，包内容与压缩前完全一致
                inst.put("pointIndices", PointIndicesCodec.decodeToPlain(points));
                instances.add(inst);
            }
            payload.put("instances", instances);
            String lidarDir = folder == null || folder.isEmpty() ? "lidarseg" : folder + "/lidarseg";
            writeJsonEntry(zip, lidarDir + "/" + map.getSampleDataToken() + "_segmentation.json", payload);
        }
    }

    /** 平台 2D 对象 -> COCO annotation（矩形框 / 多边形；其他几何返回 null 由调用方跳过）。 */
    private Map<String, Object> toCocoAnnotation(ExportAnnotationVO object, long annotationId) {
        if (!(object.getGeometry() instanceof Map<?, ?> geometry)) {
            return null;
        }
        String kind = stringValue(geometry.get("kind"));
        Map<String, Object> coco = new LinkedHashMap<>();
        coco.put("id", annotationId);
        coco.put("image_id", object.getAssetId());
        coco.put("iscrowd", COCO_ISCROWD_SINGLE);
        if (KIND_BBOX.equals(kind)) {
            double x = asNumber(geometry.get("x")).doubleValue();
            double y = asNumber(geometry.get("y")).doubleValue();
            double width = asNumber(geometry.get("width")).doubleValue();
            double height = asNumber(geometry.get("height")).doubleValue();
            coco.put("bbox", List.of(NuscenesMetaSupport.round4(x), NuscenesMetaSupport.round4(y),
                NuscenesMetaSupport.round4(width), NuscenesMetaSupport.round4(height)));
            coco.put("area", NuscenesMetaSupport.round4(width * height));
            return coco;
        }
        if (KIND_POLYGON.equals(kind) && geometry.get("points") instanceof List<?> points) {
            List<Double> flat = new ArrayList<>();
            double minX = Double.MAX_VALUE;
            double minY = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE;
            double maxY = -Double.MAX_VALUE;
            for (Object point : points) {
                if (!(point instanceof Map<?, ?> p)) {
                    continue;
                }
                double px = asNumber(p.get("x")).doubleValue();
                double py = asNumber(p.get("y")).doubleValue();
                flat.add(NuscenesMetaSupport.round4(px));
                flat.add(NuscenesMetaSupport.round4(py));
                minX = Math.min(minX, px);
                minY = Math.min(minY, py);
                maxX = Math.max(maxX, px);
                maxY = Math.max(maxY, py);
            }
            if (flat.size() < 6) {
                return null;
            }
            double width = maxX - minX;
            double height = maxY - minY;
            coco.put("segmentation", List.of(flat));
            coco.put("bbox", List.of(NuscenesMetaSupport.round4(minX), NuscenesMetaSupport.round4(minY),
                NuscenesMetaSupport.round4(width), NuscenesMetaSupport.round4(height)));
            coco.put("area", NuscenesMetaSupport.round4(width * height));
            return coco;
        }
        if (KIND_KEYPOINT.equals(kind)) {
            // 单点关键点：COCO keypoints 为单个点（x, y, v）
            double x = asNumber(geometry.get("x")).doubleValue();
            double y = asNumber(geometry.get("y")).doubleValue();
            int v = keypointVisible(geometry.get("visible"));
            List<Number> keypoints = new ArrayList<>();
            keypoints.add(NuscenesMetaSupport.round4(x));
            keypoints.add(NuscenesMetaSupport.round4(y));
            keypoints.add(v);
            coco.put("keypoints", keypoints);
            coco.put("num_keypoints", v > 0 ? 1 : 0);
            coco.put("area", 0);
            return coco;
        }
        if (KIND_SKELETON.equals(kind) && geometry.get("points") instanceof List<?> points) {
            // 骨架关键点：按模板顺序输出 keypoints 三元组 + num_keypoints（v>0 的点数）
            List<Number> keypoints = new ArrayList<>();
            int numVisible = 0;
            for (Object point : points) {
                if (!(point instanceof Map<?, ?> p)) {
                    continue;
                }
                int v = keypointVisible(p.get("visible"));
                if (v <= 0) {
                    keypoints.add(0);
                    keypoints.add(0);
                    keypoints.add(0);
                    continue;
                }
                numVisible++;
                keypoints.add(NuscenesMetaSupport.round4(asNumber(p.get("x")).doubleValue()));
                keypoints.add(NuscenesMetaSupport.round4(asNumber(p.get("y")).doubleValue()));
                keypoints.add(v);
            }
            if (keypoints.isEmpty()) {
                return null;
            }
            coco.put("keypoints", keypoints);
            coco.put("num_keypoints", numVisible);
            coco.put("area", 0);
            // 骨架边：暂存到内部键，由调用方提取后归入 COCO 类别定义
            coco.put("_skeleton_edges", geometry.get("edges") instanceof List<?> edges ? edges : List.of());
            return coco;
        }
        return null;
    }

    /** 关键点可见性归一化：0=未标注 / 1=遮挡 / 2=可见，缺失或非法按「可见」处理。 */
    private int keypointVisible(Object value) {
        int v = asInt(value);
        return v < 0 || v > 2 ? 2 : v;
    }

    /** 注册 COCO 类别（去重；空类别归入 unknown；骨架边并入类别 skeleton 定义）。 */
    private void registerCocoCategory(Map<Long, Map<String, Object>> categoryById,
                                      Long classId, String className, List<?> skeletonEdges) {
        long id = classId == null ? 0L : classId;
        Map<String, Object> category = categoryById.get(id);
        if (category == null) {
            category = new LinkedHashMap<>();
            category.put("id", id);
            category.put("name", className == null || className.isBlank() ? "unknown" : className.trim());
            category.put("supercategory", COCO_SUPER_CATEGORY);
            categoryById.put(id, category);
        }
        if (skeletonEdges != null && !skeletonEdges.isEmpty()) {
            category.putIfAbsent("skeleton", skeletonEdges);
        }
    }

    /** 统计无元数据映射的点云 cuboid 数（无法回写 sample_annotation 的对象，readme 说明用）。 */
    private int countUnmappedCuboids(ExportManifestVO manifest, NuscenesExportContext context) {
        int skipped = 0;
        for (ExportAnnotationVO object : manifest.getAnnotations()) {
            if (KIND_CUBOID.equals(stringValue(nestedKindValue(object.getGeometry())))
                && !context.mapByAsset.containsKey(object.getAssetId())) {
                skipped++;
            }
        }
        return skipped;
    }

    /** 提取资产在上传包内的原始相对路径（zip 优先直传，兜底 fileUrl 截取）。 */
    private String originalRelativePath(ExportAssetVO asset) {
        // 1. zip 上传：meta_json.zipEntry 为原始相对路径
        if (asset.getMeta() instanceof Map<?, ?> meta) {
            String zipEntry = stringValue(meta.get(META_KEY_ZIP_ENTRY));
            if (!zipEntry.isBlank()) {
                return zipEntry.replace('\\', '/');
            }
            String relativePath = stringValue(meta.get(META_KEY_RELATIVE_PATH));
            if (!relativePath.isBlank()) {
                return relativePath.replace('\\', '/');
            }
        }
        // 2. 兜底：fileUrl 形如 /uploads/{projectId}/data/{relPath}
        String fileUrl = asset.getFileUrl() == null ? "" : asset.getFileUrl();
        int dataIndex = fileUrl.indexOf(FILE_URL_DATA_SEGMENT);
        if (fileUrl.startsWith(FILE_URL_PREFIX_TEMPLATE) && dataIndex >= 0) {
            return fileUrl.substring(dataIndex + FILE_URL_DATA_SEGMENT.length());
        }
        return asset.getFileName() == null ? "" : asset.getFileName();
    }

    /** 从 MinIO 流式复制资产文件到导出包。 */
    private void copyAssetFile(ZipOutputStream zip, String entryName, String fileUrl) throws IOException {
        if (fileUrl == null || !fileUrl.startsWith(FILE_URL_PREFIX_TEMPLATE)) {
            return;
        }
        String objectKey = fileUrl.substring(1);
        try (InputStream in = minioService.openObject(objectKey)) {
            zip.putNextEntry(new ZipEntry(entryName));
            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            int len;
            while ((len = in.read(buffer)) > 0) {
                zip.write(buffer, 0, len);
            }
            zip.closeEntry();
        }
    }

    /** 写入 JSON 条目（pretty 序列化）。 */
    private void writeJsonEntry(ZipOutputStream zip, String name, Object value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(toJsonPretty(value).getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    /** 写入文本条目。 */
    private void writeTextEntry(ZipOutputStream zip, String name, String text) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    /** 取 zip 条目的父目录（含外层包裹目录）；无父目录返回空串。 */
    private String parentDirOf(String entryName) {
        String path = entryName == null ? "" : entryName.replace('\\', '/');
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(0, slash) : "";
    }

    /** 生成导出下载文件名：【项目名-批次名】.zip，非法字符与空白统一替换为下划线。 */
    private String buildExportFileName(String projectName, String batchName) {
        String project = sanitizeNamePart(projectName);
        String batch = sanitizeNamePart(batchName);
        if (project.isEmpty()) {
            project = "project";
        }
        if (batch.isEmpty()) {
            batch = "all-batches";
        }
        return project + "-" + batch + ".zip";
    }

    private String sanitizeNamePart(String name) {
        return (name == null ? "" : name.trim()).replaceAll("[/\\\\:*?\"<>|\\s]+", "_");
    }

    /** nuScenes token 规范化：合法 32 位十六进制沿用，否则生成新 token。 */
    private String nuscenesToken(String candidate) {
        String value = candidate == null ? "" : candidate.trim();
        if (value.length() == NUSCENES_TOKEN_HEX_LENGTH && value.matches("[0-9a-fA-F]+")) {
            return value.toLowerCase(Locale.ROOT);
        }
        // 兜底由随机 UUID 改为 MD5 确定性生成：同一 objectKey / instanceToken 多次导出
        // 得到同一个 token —— 否则人工框的 instance_token 每帧随机、实例链无法重建，
        // 导出包也不再具备「同输入同输出」的可复现性
        return stableToken(value);
    }

    /** 从几何对象取 kind（geometry 已解析为对象）。 */
    private Object nestedKindValue(Object geometry) {
        return geometry instanceof Map<?, ?> map ? map.get("kind") : null;
    }

    /** 取条目文件夹名：优先上传文件名（去后缀），回退 uploadNo，再回退 uploadId；无归属返回 unsorted。 */
    private String folderNameOf(Long uploadId, List<ExportAssetVO> groupAssets) {
        if (uploadId == null) {
            return "unsorted";
        }
        try {
            UploadDataVO upload = projectMapper.selectUploadById(uploadId);
            if (upload != null) {
                if (upload.getFileName() != null && !upload.getFileName().isBlank()) {
                    return sanitizeNamePart(stripExtension(upload.getFileName()));
                }
                if (upload.getUploadNo() != null && !upload.getUploadNo().isBlank()) {
                    return sanitizeNamePart(upload.getUploadNo());
                }
            }
        } catch (Exception e) {
            log.warn("查询上传数据名称失败，使用兜底命名：uploadId={}", uploadId, e);
        }
        return "upload-" + uploadId;
    }

    /** 去掉路径的首段目录（原包顶层目录），使多条目文件夹内各自归一到 nuScenes 包根。 */
    private String stripTopDir(String path) {
        if (path == null) {
            return "";
        }
        String p = path.replace('\\', '/');
        int i = p.indexOf('/');
        return i >= 0 ? p.substring(i + 1) : p;
    }

    /** sample_data.filename 通常已以 samples/ sweeps/ maps/ 开头；仅当带原包顶层目录时才去掉首段。 */
    private String sampleDataRelativePath(String filename) {
        String path = filename == null ? "" : filename.replace('\\', '/').trim();
        while (path.startsWith("./")) {
            path = path.substring(2);
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.isBlank() || isOriginalDataEntry(path)) {
            return path;
        }
        return stripTopDir(path);
    }

    /** 优先从原始上传 ZIP 补齐平台未登记为资产的数据文件（特别是 RADAR / sweep）。 */
    private void copyOriginalDataFilesFromSourceZip(ZipOutputStream zip, Long uploadId,
                                                    NuscenesExportContext context, String folderPrefix,
                                                    Set<String> writtenEntries) throws IOException {
        String sourceObjectKey = context.sourceZipByUpload.get(uploadId);
        if (sourceObjectKey != null && !sourceObjectKey.isBlank()) {
            try {
                copyDataFilesFromZipObject(zip, sourceObjectKey, folderPrefix, writtenEntries);
                return;
            } catch (Exception e) {
                log.warn("读取原始上传 ZIP 失败，改用元数据归档补齐：uploadId={}, objectKey={}",
                    uploadId, sourceObjectKey, e);
            }
        }
        String metaObjectKey = context.metaZipByUpload.get(uploadId);
        if (metaObjectKey != null && !metaObjectKey.isBlank()) {
            copyDataFilesFromZipObject(zip, metaObjectKey, folderPrefix, writtenEntries);
        }
    }

    /** 流式复制 ZIP 对象中的 samples/ sweeps/ maps/ 数据文件。 */
    private void copyDataFilesFromZipObject(ZipOutputStream zip, String objectKey, String folderPrefix,
                                            Set<String> writtenEntries) throws IOException {
        String prefix = folderPrefix == null ? "" : folderPrefix.trim();
        try (InputStream in = minioService.openObject(objectKey);
             ZipInputStream sourceZip = new ZipInputStream(in)) {
            ZipEntry sourceEntry;
            while ((sourceEntry = sourceZip.getNextEntry()) != null) {
                if (sourceEntry.isDirectory()) {
                    continue;
                }
                String rel = stripTopDir(sourceEntry.getName());
                if (!isOriginalDataEntry(rel)) {
                    continue;
                }
                String entryName = prefix.isEmpty() ? rel : prefix + "/" + rel;
                if (!writtenEntries.add(entryName)) {
                    continue;
                }
                zip.putNextEntry(new ZipEntry(entryName));
                byte[] buffer = new byte[COPY_BUFFER_SIZE];
                int len;
                while ((len = sourceZip.read(buffer)) > 0) {
                    zip.write(buffer, 0, len);
                }
                zip.closeEntry();
            }
        }
    }

    /** 仅复制 nuScenes 原始媒体目录，跳过 JSON 与其他平台文件。 */
    private boolean isOriginalDataEntry(String rel) {
        String path = rel == null ? "" : rel.replace('\\', '/');
        int slash = path.indexOf('/');
        if (slash < 0) {
            return false;
        }
        return ORIGINAL_DATA_DIRS.contains(path.substring(0, slash));
    }

    /** 组合 uploadId + 文件名，建立 sample_data 引用索引键。 */
    private String sampleDataRefKey(Long uploadId, String fileName) {
        return uploadId + SAMPLE_DATA_REF_SEPARATOR + basenameOf(fileName);
    }

    /** 提取路径末段文件名，兼容 Windows/Unix 分隔符。 */
    private String basenameOf(String path) {
        String p = path == null ? "" : path.replace('\\', '/');
        int slash = p.lastIndexOf('/');
        return slash >= 0 ? p.substring(slash + 1) : p;
    }

    /** 收集一组资产的 assetId 集合（用于限定某条目的覆盖样本与标注范围）。 */
    private Set<Long> groupAssetIds(List<ExportAssetVO> assets) {
        Set<Long> ids = new HashSet<>();
        for (ExportAssetVO asset : assets) {
            ids.add(asset.getAssetId());
        }
        return ids;
    }

    /** 取某条目的 nuScenes 版本目录（如 v1.0-mini / v1.0-trainval），供 sample_annotation 重写对齐包结构。 */
    private String versionDirOfUpload(NuscenesExportContext context, Long uploadId) {
        NuscenesMetaBundle bundle = context.bundleByUpload.get(uploadId);
        if (bundle != null && bundle.getVersionDir() != null && !bundle.getVersionDir().isBlank()) {
            return bundle.getVersionDir();
        }
        return NuscenesMetaSupport.VERSION_DIR_PREFIX + "export";
    }

    /**
     * nuScenes 导出上下文（内部装配载体）：
     * 按上传数据组织的元数据包 / 索引 / 归档对象，以及按资产组织的点云映射。
     */
    private static final class NuscenesExportContext {
        /** 上传数据ID -> 元数据包 */
        final Map<Long, NuscenesMetaBundle> bundleByUpload = new LinkedHashMap<>();
        /** 上传数据ID -> 元数据归档内 sample_annotation.json 所在目录（去原包顶层后），供重写步骤对齐包结构 */
        final Map<Long, String> sampleAnnotationDirByUpload = new LinkedHashMap<>();
        /** 上传数据ID -> 元数据归档内 attribute.json 所在目录（去原包顶层后），供补充字典对齐包结构 */
        final Map<Long, String> attributeDirByUpload = new LinkedHashMap<>();
        /** 上传数据ID -> 元数据归档内 category.json / instance.json 所在目录（去原包顶层后） */
        final Map<Long, String> categoryDirByUpload = new LinkedHashMap<>();
        final Map<Long, String> instanceDirByUpload = new LinkedHashMap<>();
        final Map<Long, String> sampleDataDirByUpload = new LinkedHashMap<>();
        /** 上传数据ID -> attribute token -> 行（原始行 + 平台自定义属性行），导出时统一重写 attribute.json */
        final Map<Long, Map<String, Map<String, Object>>> attributeRowsByUpload = new LinkedHashMap<>();
        /** 上传数据ID -> category token -> 行（原始行 + 平台自定义类别行），导出时统一重写 category.json */
        final Map<Long, Map<String, Map<String, Object>>> categoryRowsByUpload = new LinkedHashMap<>();
        /** uploadId|basename -> sample_data 引用，用于相机帧 sidecar 补齐 sample/sample_data token */
        final Map<String, SampleDataRef> sampleDataRefByUploadFileName = new LinkedHashMap<>();
        /** 上传数据ID -> 元数据索引 */
        final Map<Long, NuscenesIndex> indexByUpload = new LinkedHashMap<>();
        /** 上传数据ID -> 元数据归档的 MinIO 对象Key */
        final Map<Long, String> metaZipByUpload = new LinkedHashMap<>();
        /** 上传数据ID -> 原始上传 ZIP 的 MinIO 对象Key（用于还原未入库资产） */
        final Map<Long, String> sourceZipByUpload = new LinkedHashMap<>();
        /** 点云资产ID -> 元数据映射（sample/sample_data token） */
        final Map<Long, LabelNuscenesAssetMap> mapByAsset = new HashMap<>();
        /** 元数据归档内第一个 JSON 的完整父目录（如 nusc/v1.0-trainval），供 sample_annotation 重写对齐包结构 */
        String metaDirPrefix = null;

        boolean hasMeta() {
            return !bundleByUpload.isEmpty();
        }

        /** 第一个可用版本目录（sample_annotation.json 重写文件的存放目录）。 */
        String firstVersionDir() {
            if (metaDirPrefix != null && !metaDirPrefix.isBlank()) {
                return metaDirPrefix;
            }
            for (NuscenesMetaBundle bundle : bundleByUpload.values()) {
                if (bundle.getVersionDir() != null && !bundle.getVersionDir().isBlank()) {
                    return bundle.getVersionDir();
                }
            }
            return NuscenesMetaSupport.VERSION_DIR_PREFIX + "export";
        }
    }

    /** sample_data 轻量引用，用于未按资产登记的相机帧 sidecar 关联。 */
    private static final class SampleDataRef {
        final String sampleToken;
        final String sampleDataToken;

        SampleDataRef(String sampleToken, String sampleDataToken) {
            this.sampleToken = sampleToken;
            this.sampleDataToken = sampleDataToken;
        }
    }

    /**
     * 生成 readme.txt（数据集概览与格式约定说明）。
     */
    private String buildReadme(String format, ExportManifestVO manifest, int skipped) {
        StringBuilder text = new StringBuilder();
        text.append("标注平台数据集导出\n");
        text.append("==================\n");
        text.append("项目：").append(manifest.getProjectName())
                .append("（ID: ").append(manifest.getProjectId()).append("）\n");
        if (manifest.getBatchId() != null) {
            text.append("批次：").append(manifest.getBatchName())
                    .append("（ID: ").append(manifest.getBatchId()).append("）\n");
        } else {
            text.append("批次：全部批次（项目全量合格数据）\n");
        }
        text.append("格式：").append(format).append('\n');
        text.append("导出时间：")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .append('\n');
        text.append("资产数：").append(manifest.getAssetCount())
                .append("（仅含通过全部审核节点的合格数据）\n");
        text.append("标注对象数：").append(manifest.getObjectCount()).append('\n');
        if (FORMAT_JSON.equals(format)) {
            text.append("\n[JSON 格式说明]\n");
            text.append("annotations.json 为平台原生结构：\n");
            text.append("- assets：合格数据资产清单（assetId/mediaType/fileName/fileUrl/fileSize/meta）\n");
            text.append("- annotations：标注对象清单（assetId/annotationId/objectType/classId/className/geometry/attributes）\n");
            text.append("- geometry.kind 取值：bbox（矩形框 x/y/width/height）、polygon（points 顶点数组）、\n");
            text.append("  brush/mask（画笔掩码）、polyline/keypoint（points）、cuboid（立体框 center/size/rotationZ）、\n");
            text.append("  point_cloud_instance/segmentation（pointIndices 点云点索引）\n");
        } else if (FORMAT_NUSCENES.equals(format)) {
            text.append("\n[nuScenes 原格式说明]\n");
            text.append("- 包结构与原数据集一致：samples/、sweeps/、maps/ 下的文件优先从原始上传 ZIP 完整还原，\n");
            text.append("  包括平台未登记为资产的 RADAR/sweep 文件；原始 ZIP 不可用时仅保留实际写入文件\n");
            text.append("  对应的 sample_data 行，避免悬空引用；v1.0-*/ 中为元数据 JSON\n");
            text.append("- v1.0-*/sample_annotation.json 为重写文件：仅有平台 3D 框的样本使用平台标注\n");
            text.append("  （雷达传感器系 -> ego -> global 逆变换），未编辑样本保留原始行\n");
            text.append("- v1.0-*/attribute.json、category.json、instance.json 会按最终 sample_annotation 重写，\n");
            text.append("  保证属性字典、类别字典、实例数量与首末标注 token 一致\n");
            text.append("- 预标注来源的框沿用原 annotation token / instance_token / category_token / visibility_token，\n");
            text.append("  与官方数据无缝衔接；实例属性写入 attribute_tokens 数组，人工新画的框 num_lidar/num_radar_pts 为 0\n");
            text.append("- platform_attributes/global_attrs.json 记录帧级全局属性、帧有效性、无效原因与批注\n");
            text.append("  （平台扩展，不属于 nuScenes 官方表）\n");
            text.append("- 平台 3D 框仅采集绕竖直轴偏航（rotationZ），导出四元数的 pitch/roll 恒为 0\n");
            if (skipped > 0) {
                text.append("- 有 ").append(skipped)
                    .append(" 个点云立体框因资产缺少 nuScenes 元数据映射（非 nuScenes 数据包）未回写 sample_annotation\n");
            }
            text.append("- 2D 图片标注输出行业通用 COCO 格式（coco_annotations.json：images/annotations/categories，\n");
            text.append("  含矩形框 bbox 与多边形 segmentation；无 2D 标注时省略该文件）\n");
            text.append("- 点云实例分割/语义分割输出 lidarseg/{点云 sample_data token}_segmentation.json：\n");
            text.append("  含该点云全部实例（instanceToken/category/classId/pointIndices 点索引列表），仅 nuScenes 点云资产有映射时生成\n");
        } else if (FORMAT_POINT.equals(format)) {
            text.append("\n[POINT（点位）格式说明]\n");
            text.append("- 单文件 point_annotations.json：JSON 数组（对齐外部「无人机」参考系统），每帧一个对象\n");
            text.append("- 字段：batchId/_id（平台内部数值 ID，便于与系统内部数据回查对应）、lensFrame、index（帧序号）、info（图片 URL）、\n");
            text.append("  size{width,height}（图片像素尺寸：入库时预存，历史数据导出时回读；点云等无尺寸置 0）、labels（标注列表）\n");
            text.append("- labels 字段：_id/id/label/drawType(RECTANGLE/POLYGON/point)/group/points(扁平坐标数组 [x,y,...])/zIndex/attr\n");
            text.append("- 导出全部 2D 几何（矩形框 bbox→RECTANGLE / 多边形 polygon→POLYGON / 关键点 keypoint→point / 骨架 skeleton→point）；其他几何类型已跳过");
            if (skipped > 0) {
                text.append("（本次跳过 ").append(skipped).append(" 个）");
            }
            text.append('\n');
        } else if (FORMAT_YOLO.equals(format)) {
            text.append("\n[YOLO 格式说明]\n");
            text.append("- class.json：类别定义数组，字段 baseLabel（类别名）/ label（类别 id）/ color（颜色）\n");
            text.append("- labels/{图片名}.jpg：原图；labels/{图片名}.txt：同名标签文件\n");
            text.append("- txt 每行：class_id cx cy w h（中心点与宽高，相对原图宽高归一化到 0~1）\n");
            text.append("- 类别 id 按出现顺序从 0 连续编号（平台内部 classId 不连续，对应关系以 class.json 为准）\n");
            text.append("- 仅导出 2D 矩形框：YOLO 检测格式只支持矩形，其他几何类型已跳过");
            if (skipped > 0) {
                text.append("（本次跳过 ").append(skipped).append(" 个）");
            }
            text.append('\n');
        } else if (FORMAT_COCO.equals(format)) {
            text.append("\n[COCO 格式说明]\n");
            text.append("- coco.json：标准 COCO，含 images[]（id/width/height/file_name）、\n");
            text.append("  categories[]（id 从 0 起连续编号 / name / supercategory）、annotations[]\n");
            text.append("- images/{图片名}：原图（与 coco.json 的 file_name 同名，纯文件名不带目录）\n");
            text.append("- annotations 每行：id / image_id / category_id / bbox（绝对像素 x,y,w,h）/ area / iscrowd / segmentation\n");
            text.append("- 矩形框的 segmentation 为空数组；多边形写扁平顶点，bbox 取其外接矩形\n");
            text.append("- 类别 id 按出现顺序从 0 连续编号（平台内部 classId 不连续，对应关系以 categories 为准）\n");
            text.append("- 仅导出 2D 矩形框与多边形：其他几何类型已跳过");
            if (skipped > 0) {
                text.append("（本次跳过 ").append(skipped).append(" 个）");
            }
            text.append('\n');
        } else {
            text.append("\n[KITTI 格式说明]\n");
            text.append("label_2/{资产文件名去后缀}.txt，每行一个对象，共 15 列：\n");
            text.append("type truncated occluded alpha x1 y1 x2 y2 l h w x y z ry\n");
            text.append("- 仅导出框类对象：2D 矩形框（bbox）与 3D 立体框（cuboid）；其他几何类型已跳过");
            if (skipped > 0) {
                text.append("（本次跳过 ").append(skipped).append(" 个）");
            }
            text.append('\n');
            text.append("- 2D 矩形框仅 bbox 四列有效，3D 各列为无效占位（-1 / -1000 / -10）\n");
            text.append("- 3D 立体框：l=长(x) h=高(z) w=宽(y)，location 为框底部中心，ry 为绕竖直轴的偏航角（弧度）\n");
            text.append("- 坐标系与平台点云一致（X/Y 为水平面、Z 轴向上），未做 KITTI 相机坐标系变换\n");
            text.append("- truncated/occluded/alpha 平台未采集，固定为 -1 / -1 / -10（未知 / 不可用）\n");
        }
        return text.toString();
    }

    /** 文件名去掉扩展名（空名回退为 asset） */
    private String stripExtension(String fileName) {
        String name = fileName == null || fileName.isBlank() ? "asset" : fileName.trim();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** pretty 序列化（失败时回退紧凑格式） */
    private String toJsonPretty(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return toJson(value);
        }
    }

    /** 文本截断（超长失败原因入库保护） */
    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    /** KITTI 导出装配结果：文件内容集合 + 不支持几何的对象跳过数 */
    private record KittiResult(Map<String, String> files, int skipped) {
    }
}
