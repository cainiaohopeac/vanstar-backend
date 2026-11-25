package org.dromara.label.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.label.common.MediaType;
import org.dromara.label.common.NuscenesMetaSupport;
import org.dromara.label.common.NuscenesMetaSupport.NuscenesIndex;
import org.dromara.label.dto.NuscenesArchiveRequest;
import org.dromara.label.dto.NuscenesMetaBundle;
import org.dromara.label.dto.NuscenesParseDTO;
import org.dromara.label.entity.LabelNuscenesAssetMap;
import org.dromara.label.entity.LabelNuscenesMeta;
import org.dromara.label.mapper.NuscenesMapper;
import org.dromara.label.mapper.PreAnnotationMapper;
import org.dromara.label.mapper.SequenceMapper;
import org.dromara.label.service.BaseService;
import org.dromara.label.service.MinioService;
import org.dromara.label.service.NuscenesImportService;
import org.dromara.label.service.NuscenesMetaCollector;
import org.dromara.label.service.PreAnnotationService;
import org.dromara.label.vo.NuscenesParseResultVO;
import org.dromara.label.vo.PreAnnotationAssetVO;
import org.dromara.label.vo.PreAnnotationResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * nuScenes 数据集导入域服务实现。
 *
 * <p>三个职责共用一套元数据语义（{@link NuscenesMetaSupport}）：
 * 1. 上传链路的元数据流式收集与归档（v1.0-* JSON 原样打包入 MinIO）；
 * 2. 解析归档建立「点云资产 - 同帧相机图」关联（label_nuscenes_asset_map）；
 * 3. 触发预标注（复用预标注域的坐标转换与落库）。</p>
 */
@Service
public class NuscenesImportServiceImpl extends BaseService implements NuscenesImportService {

    // ---------------- 常量（无魔法值） ----------------

    /** 归档状态：已解析 */
    private static final String META_STATUS_PARSED = "parsed";

    /** 归档状态：解析失败 */
    private static final String META_STATUS_FAILED = "failed";

    /** 归档初始状态（归档完成、尚未解析） */
    private static final String META_STATUS_ARCHIVED = "archived";

    /** 元数据归档总字节上限（超出拒绝归档，防止内存溢出） */
    private static final long META_ARCHIVE_LIMIT_BYTES = 256L * 1024L * 1024L;

    /** zip 条目读取缓冲区大小（字节） */
    private static final int ENTRY_BUFFER_SIZE = 8192;

    /** 归档包内文件后缀 */
    private static final String META_ARCHIVE_SUFFIX = "/meta.zip";

    /** 归档 zip 的 Content-Type */
    private static final String CONTENT_TYPE_ZIP = "application/zip";

    /** 解析摘要模板：相机关联 {}/ 预标注 {}/{} 资产、{} 框 */
    private static final String REMARK_TEMPLATE = "相机图关联 %d 条；预标注匹配 %d/%d 资产、生成 %d 框";

    /** MinIO 流式上传的大小未知标记（由 SDK 自动分片） */
    private static final long UNKNOWN_SIZE = -1L;

    @Autowired
    private NuscenesMapper nuscenesMapper;

    @Autowired
    private PreAnnotationMapper preAnnotationMapper;

    @Autowired
    private PreAnnotationService preAnnotationService;

    @Autowired
    private MinioService minioService;

    /** 序列域数据访问（落库 sensor / calibration / ego_pose 复用） */
    @Autowired
    private SequenceMapper sequenceMapper;

    // ---------------- 元数据收集与归档 ----------------

    @Override
    public NuscenesMetaCollector newCollector(NuscenesArchiveRequest request) {
        if (request == null || request.getUploadId() == null) {
            throw new ServiceException("元数据归档上下文不完整：缺少上传数据ID");
        }
        return new StreamingMetaCollector(request);
    }

    @Override
    public NuscenesParseResultVO parseUpload(NuscenesParseDTO dto) {
        if (dto == null || dto.getUploadId() == null) {
            throw new ServiceException("上传数据ID不能为空");
        }
        // 1. 定位元数据归档（一条上传数据一份）
        LabelNuscenesMeta meta = nuscenesMapper.selectMetaByUploadId(dto.getUploadId());
        if (meta == null) {
            // 非 nuScenes 数据（无元数据归档）：无需解析，友好返回，供导入链路自动触发时安全跳过，不抛错阻断导入
            NuscenesParseResultVO skipped = new NuscenesParseResultVO();
            skipped.setUploadId(dto.getUploadId());
            skipped.setStatus("skipped");
            skipped.setRemark("该上传数据没有 nuScenes 元数据归档，无需解析");
            skipped.setCameraMapCount(0);
            skipped.setPreAnnotationAssetTotal(0);
            skipped.setPreAnnotationMatched(0);
            skipped.setPreAnnotationBoxTotal(0);
            return skipped;
        }
        // 2. 读取归档并解析为元数据包（失败回写归档状态，不影响主流程返回错误信息）
        NuscenesMetaBundle bundle;
        try {
            bundle = readBundle(meta);
        } catch (ServiceException e) {
            markMetaFailed(meta, e.getMessage());
            throw e;
        }
        // 3. 校验必需元数据表（缺表无法建映射与预标注）
        for (String required : NuscenesMetaSupport.REQUIRED_JSON) {
            if (!bundle.getTables().containsKey(required)) {
                String message = "元数据归档缺少 " + required + ".json，无法解析";
                markMetaFailed(meta, message);
                throw new ServiceException(message);
            }
        }
        // 4. 建立点云资产与同帧相机图的关联（覆盖式：先软删旧关联再写入）
        List<LabelNuscenesAssetMap> maps = buildAssetMaps(meta, bundle);
        // 4.5 落库传感器/标定/位姿（导出还原 + 前端投影联动的前置）
        persistSensorCalib(meta, bundle);
        // 5. 自动预标注（复用预标注域：global -> sensor 坐标转换后按文件名匹配落库）
        PreAnnotationResultVO preResult = preAnnotationService.preAnnotateAssets(
            preAnnotationMapper.selectAssetsByUpload(dto.getUploadId()), bundle);
        // 6. 回写归档状态与摘要
        String remark = String.format(REMARK_TEMPLATE,
            maps.size(), preResult.getMatchedAsset(), preResult.getAssetTotal(), preResult.getBoxTotal());
        // 实例链能算出来时补一段漏标摘要：哪些 instance 在自己的首末帧之间漏了帧
        int gapInstance = preResult.getGapInstanceCount() == null ? 0 : preResult.getGapInstanceCount();
        if (gapInstance > 0) {
            remark = remark + String.format("；实例 %s 个，其中 %s 个存在漏标（合计缺 %s 帧）",
                preResult.getInstanceCount(), gapInstance, preResult.getMissingFrameTotal());
        }
        meta.setStatus(META_STATUS_PARSED);
        meta.setRemark(remark);
        meta.setUserId(currentUserId());
        nuscenesMapper.updateMetaStatus(meta);
        // 7. 组装返回
        NuscenesParseResultVO result = new NuscenesParseResultVO();
        result.setUploadId(dto.getUploadId());
        result.setMetaId(meta.getId());
        result.setVersionDir(meta.getVersionDir());
        result.setStatus(META_STATUS_PARSED);
        result.setRemark(remark);
        result.setCameraMapCount(maps.size());
        result.setPreAnnotationAssetTotal(preResult.getAssetTotal());
        result.setPreAnnotationMatched(preResult.getMatchedAsset());
        result.setPreAnnotationBoxTotal(preResult.getBoxTotal());
        result.setInstanceCount(preResult.getInstanceCount());
        result.setGapInstanceCount(preResult.getGapInstanceCount());
        result.setMissingFrameTotal(preResult.getMissingFrameTotal());
        return result;
    }

    @Override
    public NuscenesMetaBundle loadBundle(Long uploadId) {
        LabelNuscenesMeta meta = nuscenesMapper.selectMetaByUploadId(uploadId);
        if (meta == null) {
            return null;
        }
        return readBundle(meta);
    }

    @Override
    public LabelNuscenesMeta metaOfUpload(Long uploadId) {
        return nuscenesMapper.selectMetaByUploadId(uploadId);
    }

    // ---------------- 内部：归档读取与映射构建 ----------------

    /** 读取 MinIO 归档并解析为元数据包（按白名单表名取第一个遇到的文件）。 */
    private NuscenesMetaBundle readBundle(LabelNuscenesMeta meta) {
        Map<String, List<Map<String, Object>>> tables = new HashMap<>();
        try (InputStream in = minioService.openObject(meta.getMetaObjectKey());
             ZipInputStream zip = new ZipInputStream(new BufferedInputStream(in))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                // 1. 仅解析白名单内的元数据表，同表多文件（多版本目录）取第一个
                String key = NuscenesMetaSupport.jsonKeyOf(entry.getName());
                if (!NuscenesMetaSupport.ACCEPTED_JSON.contains(key) || tables.containsKey(key)) {
                    continue;
                }
                // 2. 读取并反序列化为行集合
                byte[] bytes = readAll(zip);
                tables.put(key, objectMapper.readValue(bytes, new TypeReference<List<Map<String, Object>>>() {
                }));
            }
        } catch (IOException e) {
            throw new ServiceException("读取 nuScenes 元数据归档失败：" + e.getMessage());
        }
        NuscenesMetaBundle bundle = new NuscenesMetaBundle();
        bundle.setVersionDir(meta.getVersionDir());
        bundle.setSourceFile(meta.getMetaObjectKey());
        bundle.setTables(tables);
        return bundle;
    }

    /**
     * 建立点云资产与同帧相机图的关联（覆盖式写入 label_nuscenes_asset_map）。
     * <p>逻辑：
     * 1. 构建「文件名键 -&gt; 图片资产」索引（同一上传数据范围内）；
     * 2. 遍历点云资产，按文件名键匹配点云 sample_data 定位样本；
     * 3. 取同样本的全部相机 sample_data，按文件名键关联图片资产，一通道一行；
     * 4. 先软删该上传数据的旧关联，再批量写入本次结果。</p>
     */
    private List<LabelNuscenesAssetMap> buildAssetMaps(LabelNuscenesMeta meta, NuscenesMetaBundle bundle) {
        List<PreAnnotationAssetVO> assets = preAnnotationMapper.selectAssetsByUpload(meta.getUploadId());
        List<LabelNuscenesAssetMap> maps = new ArrayList<>();
        if (assets == null || assets.isEmpty()) {
            return maps;
        }
        // 1. 图片资产双索引：路径键（含通道目录，file_url 保留完整路径）优先，
        //    纯文件名键兜底——自采数据集「同帧各相机同名、仅目录不同」时只有路径键能区分通道
        Map<String, PreAnnotationAssetVO> imageByPathKey = new HashMap<>();
        Map<String, PreAnnotationAssetVO> imageByFileKey = new HashMap<>();
        for (PreAnnotationAssetVO asset : assets) {
            if (MediaType.IMAGE.equals(asset.getMediaType())) {
                imageByPathKey.putIfAbsent(
                    NuscenesMetaSupport.dataPathKey(
                        asset.getFileUrl() != null && !asset.getFileUrl().isBlank()
                            ? asset.getFileUrl() : asset.getFileName()),
                    asset);
                imageByFileKey.putIfAbsent(NuscenesMetaSupport.dataFileKey(asset.getFileName()), asset);
            }
        }
        NuscenesIndex index = NuscenesMetaSupport.buildIndex(bundle.getTables());
        // 2/3. 点云资产定位样本，逐相机通道建关联
        for (PreAnnotationAssetVO asset : assets) {
            if (!MediaType.POINT_CLOUD.equals(asset.getMediaType())) {
                continue;
            }
            Map<String, Object> lidarSampleData =
                index.sampleDataByFileKey.get(
                    asset.getFileUrl() != null && !asset.getFileUrl().isBlank()
                        ? NuscenesMetaSupport.dataPathKey(asset.getFileUrl())
                        : NuscenesMetaSupport.dataFileKey(asset.getFileName()));
            if (lidarSampleData == null) {
                lidarSampleData =
                    index.sampleDataByFileKey.get(NuscenesMetaSupport.dataFileKey(asset.getFileName()));
            }
            if (lidarSampleData == null) {
                continue;
            }
            String sampleToken = NuscenesMetaSupport.text(lidarSampleData.get("_sampleToken"));
            String sampleDataToken = NuscenesMetaSupport.text(lidarSampleData.get("token"));
            long lidarTs = timestampOf(lidarSampleData);
            // 一个 sample 下同一相机通道可能有多条 sample_data（自采数据把相邻帧也挂在
            // 同一 sample 下）：先按通道收集「能匹配到图片资产」的候选，再取与点云帧
            // timestamp 最接近的一条 —— 不然每帧每通道建两条关联，相机参考图整批翻倍
            Map<String, Map<String, Object>> bestByChannel = new LinkedHashMap<>();
            Map<String, Long> bestDeltaByChannel = new HashMap<>();
            for (Map<String, Object> sampleData : index.sampleDataBySample.getOrDefault(sampleToken, List.of())) {
                if (!NuscenesMetaSupport.isCameraSampleData(sampleData, index)) {
                    continue;
                }
                // 相机行匹配：路径键优先（通道目录区分同名相机图），文件名键兜底（官方数据集）
                PreAnnotationAssetVO cameraAsset =
                    imageByPathKey.get(NuscenesMetaSupport.dataPathKey(NuscenesMetaSupport.text(sampleData.get("filename"))));
                if (cameraAsset == null) {
                    cameraAsset =
                        imageByFileKey.get(NuscenesMetaSupport.dataFileKey(NuscenesMetaSupport.text(sampleData.get("filename"))));
                }
                if (cameraAsset == null) {
                    continue;
                }
                String channel = NuscenesMetaSupport.sensorChannelOf(sampleData, index);
                long delta = Math.abs(timestampOf(sampleData) - lidarTs);
                Long prevDelta = bestDeltaByChannel.get(channel);
                if (prevDelta == null || delta < prevDelta) {
                    bestDeltaByChannel.put(channel, delta);
                    bestByChannel.put(channel, sampleData);
                }
            }
            for (Map.Entry<String, Map<String, Object>> entry : bestByChannel.entrySet()) {
                Map<String, Object> sampleData = entry.getValue();
                PreAnnotationAssetVO cameraAsset =
                    imageByPathKey.get(NuscenesMetaSupport.dataPathKey(NuscenesMetaSupport.text(sampleData.get("filename"))));
                if (cameraAsset == null) {
                    cameraAsset =
                        imageByFileKey.get(NuscenesMetaSupport.dataFileKey(NuscenesMetaSupport.text(sampleData.get("filename"))));
                }
                if (cameraAsset == null) {
                    continue;
                }
                LabelNuscenesAssetMap map = new LabelNuscenesAssetMap();
                map.setProjectId(meta.getProjectId());
                map.setUploadId(meta.getUploadId());
                map.setAssetId(asset.getAssetId());
                map.setSampleToken(sampleToken);
                map.setSampleDataToken(sampleDataToken);
                map.setCameraFileUrl(cameraAsset.getFileUrl());
                map.setCameraAssetId(cameraAsset.getAssetId());
                map.setCameraChannel(entry.getKey());
                // 相机标定（内外参 + 图片尺寸）：供前端投影联动
                fillCameraCalib(map, sampleData, index);
                // 点云传感器（LIDAR）外参：投影的起点坐标系
                fillLidarCalib(map, lidarSampleData, index);
                // 该帧自车位姿：前端据此把各帧点云统一到全局坐标系
                fillEgoPose(map, lidarSampleData, index);
                map.setUserId(currentUserId());
                maps.add(map);
            }
        }
        // 3.5 兜底去重：同一（点云资产, 相机通道）只保留一条关联（按 id 序首条胜出）
        List<LabelNuscenesAssetMap> dedupMaps = new ArrayList<>(maps.size());
        Set<String> seenMapKeys = new HashSet<>();
        for (LabelNuscenesAssetMap map : maps) {
            if (seenMapKeys.add(map.getAssetId() + "#" + map.getCameraChannel())) {
                dedupMaps.add(map);
            }
        }
        maps = dedupMaps;
        // 4. 覆盖式落库
        if (!maps.isEmpty()) {
            nuscenesMapper.softDeleteAssetMapsByUploadId(meta.getUploadId(), currentUserId());
            nuscenesMapper.insertAssetMaps(maps);
        }
        return maps;
    }

    /** sample_data.timestamp 宽松取值（数值/字符串都能解析；缺失或非法返回 Long.MAX_VALUE，排序时自然靠后） */
    private static long timestampOf(Map<String, Object> row) {
        Object ts = row == null ? null : row.get("timestamp");
        if (ts instanceof Number n) {
            return n.longValue();
        }
        if (ts != null) {
            try {
                return Long.parseLong(String.valueOf(ts).trim());
            } catch (NumberFormatException ignored) {
                // 落到默认值
            }
        }
        return Long.MAX_VALUE;
    }

    /**
     * 落库传感器 / 标定 / 位姿（复用序列域表）：
     * 导出还原 ego_pose / calibrated_sensor / sensor 必需，也是前端投影联动的前置。
     * 按 project+channel 复用 sensor，按 token 查重 calibration / ego_pose，重复解析不堆积。
     */
    private void persistSensorCalib(LabelNuscenesMeta meta, NuscenesMetaBundle bundle) {
        List<Map<String, Object>> sensors = bundle.getTables().get("sensor");
        if (sensors == null || sensors.isEmpty()) {
            return;
        }
        long userId = currentUserId();
        Long projectId = meta.getProjectId();
        // 1. 传感器（按 project + channel 复用）
        Map<String, Long> sensorIdByToken = new HashMap<>();
        for (Map<String, Object> sensor : sensors) {
            String token = NuscenesMetaSupport.text(sensor.get("token"));
            String channel = NuscenesMetaSupport.text(sensor.get("channel"));
            Long existing = sequenceMapper.selectSensorId(projectId, channel);
            long id;
            if (existing != null) {
                id = existing;
            } else {
                Map<String, Object> p = new HashMap<>();
                p.put("projectId", projectId);
                p.put("channel", channel);
                p.put("modality", NuscenesMetaSupport.text(sensor.get("modality")));
                p.put("userId", userId);
                sequenceMapper.insertSensor(p);
                id = asLong(p.get("id"));
            }
            sensorIdByToken.put(token, id);
        }
        // 2. 标定（按 token 查重）
        List<Map<String, Object>> calibs = bundle.getTables().get("calibrated_sensor");
        if (calibs != null) {
            for (Map<String, Object> calib : calibs) {
                String token = NuscenesMetaSupport.text(calib.get("token"));
                if (sequenceMapper.selectCalibrationIdByToken(token) != null) {
                    continue;
                }
                Long sensorId = sensorIdByToken.get(NuscenesMetaSupport.text(calib.get("sensor_token")));
                if (sensorId == null) {
                    continue;
                }
                Map<String, Object> p = new HashMap<>();
                p.put("sensorId", sensorId);
                p.put("token", token);
                p.put("translation", toJson(calib.get("translation")));
                p.put("rotation", toJson(calib.get("rotation")));
                p.put("cameraIntrinsic", toJson(calib.get("camera_intrinsic")));
                p.put("userId", userId);
                sequenceMapper.insertCalibration(p);
            }
        }
        // 3. 位姿（按 token 查重）
        List<Map<String, Object>> poses = bundle.getTables().get("ego_pose");
        if (poses != null) {
            for (Map<String, Object> pose : poses) {
                String token = NuscenesMetaSupport.text(pose.get("token"));
                if (sequenceMapper.selectEgoPoseIdByToken(token) != null) {
                    continue;
                }
                Object ts = pose.get("timestamp");
                Map<String, Object> p = new HashMap<>();
                p.put("token", token);
                p.put("translation", toJson(pose.get("translation")));
                p.put("rotation", toJson(pose.get("rotation")));
                p.put("timestampUs", ts instanceof Number n ? n.longValue() : 0L);
                p.put("userId", userId);
                sequenceMapper.insertEgoPose(p);
            }
        }
    }

    /** 填充相机标定：内参 [fx,fy,cx,cy] + 外参（四元数/平移）+ 图片尺寸。 */
    private void fillCameraCalib(LabelNuscenesAssetMap map, Map<String, Object> sampleData, NuscenesIndex index) {
        Map<String, Object> calib = index.calibratedSensorByToken.get(
            NuscenesMetaSupport.text(sampleData.get("calibrated_sensor_token")));
        if (calib == null) {
            return;
        }
        map.setCameraRotation(toJson(calib.get("rotation")));
        map.setCameraTranslation(toJson(calib.get("translation")));
        map.setCameraIntrinsic(toJson(intrinsic4(calib.get("camera_intrinsic"))));
        if (sampleData.get("width") instanceof Number w) {
            map.setCameraWidth(w.intValue());
        }
        if (sampleData.get("height") instanceof Number h) {
            map.setCameraHeight(h.intValue());
        }
    }

    /** 填充点云传感器（LIDAR）外参：投影坐标变换的起点。 */
    private void fillLidarCalib(LabelNuscenesAssetMap map, Map<String, Object> lidarSampleData, NuscenesIndex index) {
        Map<String, Object> calib = index.calibratedSensorByToken.get(
            NuscenesMetaSupport.text(lidarSampleData.get("calibrated_sensor_token")));
        if (calib == null) {
            return;
        }
        map.setLidarRotation(toJson(calib.get("rotation")));
        map.setLidarTranslation(toJson(calib.get("translation")));
    }

    /**
     * 填充该帧自车位姿（ego pose）：取自本帧点云 sample_data 的 ego_pose_token。
     * 逐帧点云原本各自在自己的雷达/自车坐标系里，自车行驶会让静态背景在切帧时整体漂移；
     * 下发位姿后前端可把各帧统一变换到同一全局坐标系，背景才能真正静止。
     */
    private void fillEgoPose(LabelNuscenesAssetMap map, Map<String, Object> lidarSampleData, NuscenesIndex index) {
        Map<String, Object> egoPose =
            index.egoPoseByToken.get(NuscenesMetaSupport.text(lidarSampleData.get("ego_pose_token")));
        if (egoPose == null) {
            return;
        }
        map.setEgoRotation(toJson(egoPose.get("rotation")));
        map.setEgoTranslation(toJson(egoPose.get("translation")));
    }

    /** 从 nuScenes 3x3 内参矩阵提取 [fx, fy, cx, cy]。 */
    private List<Double> intrinsic4(Object cameraIntrinsic) {
        if (!(cameraIntrinsic instanceof List<?> matrix) || matrix.size() < 2) {
            return List.of();
        }
        List<Double> row0 = asDoubleList(matrix.get(0));
        List<Double> row1 = asDoubleList(matrix.get(1));
        if (row0.size() < 3 || row1.size() < 3) {
            return List.of();
        }
        return List.of(row0.get(0), row1.get(1), row0.get(2), row1.get(2));
    }

    private List<Double> asDoubleList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Double> result = new ArrayList<>();
        for (Object item : list) {
            result.add(item instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(item)));
        }
        return result;
    }

    /** 回写归档失败状态。 */
    private void markMetaFailed(LabelNuscenesMeta meta, String reason) {
        LabelNuscenesMeta update = new LabelNuscenesMeta();
        update.setId(meta.getId());
        update.setStatus(META_STATUS_FAILED);
        update.setRemark(reason);
        update.setUserId(currentUserId());
        nuscenesMapper.updateMetaStatus(update);
    }

    /** 读取当前 zip 条目的全部字节。 */
    private byte[] readAll(InputStream stream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[ENTRY_BUFFER_SIZE];
        int len;
        while ((len = stream.read(chunk)) > 0) {
            buffer.write(chunk, 0, len);
        }
        return buffer.toByteArray();
    }

    // ---------------- 元数据流式收集器 ----------------

    /**
     * 流式收集器实现：ingestZip 扫描期间把 v1.0-* JSON 缓冲在内存，
     * finish 时原样打包（保留原始条目路径）上传 MinIO 并登记记录。
     */
    private final class StreamingMetaCollector implements NuscenesMetaCollector {

        private final NuscenesArchiveRequest request;

        /** 收集的元数据条目：原始 zip 条目名 -&gt; 内容 */
        private final Map<String, byte[]> collected = new LinkedHashMap<>();

        /** 收集总字节数（超限保护） */
        private long totalBytes = 0L;

        /** 探测到的版本目录（取第一个元数据条目） */
        private String versionDir = "";

        private StreamingMetaCollector(NuscenesArchiveRequest request) {
            this.request = request;
        }

        @Override
        public void collect(String entryName, InputStream entryStream) throws IOException {
            // 1. 非元数据 JSON（不在 v1.0-* 目录下）直接跳过
            if (!NuscenesMetaSupport.isMetaJsonEntry(entryName)) {
                return;
            }
            // 2. 收集 v1.0-* 目录下全部 JSON（含 scene/log/map/splits 等）：
            //    解析阶段仍按 ACCEPTED_JSON 白名单过滤，多收不影响解析，但导出能完整还原原包结构
            if (collected.containsKey(entryName)) {
                return;
            }
            // 3. 读入内存并累计超限保护
            byte[] bytes = readAll(entryStream);
            if (totalBytes + bytes.length > META_ARCHIVE_LIMIT_BYTES) {
                throw new ServiceException("nuScenes 元数据超过归档上限 "
                    + (META_ARCHIVE_LIMIT_BYTES / 1024L / 1024L) + "MB，请分目录分批上传");
            }
            collected.put(entryName, bytes);
            totalBytes += bytes.length;
            if (versionDir.isEmpty()) {
                versionDir = NuscenesMetaSupport.versionDirOf(entryName);
            }
        }

        @Override
        public boolean hasMeta() {
            return !collected.isEmpty();
        }

        @Override
        public LabelNuscenesMeta finish() {
            // 1. 打包：保留原始条目路径原样写入归档 zip
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
                for (Map.Entry<String, byte[]> entry : collected.entrySet()) {
                    zip.putNextEntry(new ZipEntry(entry.getKey()));
                    zip.write(entry.getValue());
                    zip.closeEntry();
                }
            } catch (IOException e) {
                throw new ServiceException("打包 nuScenes 元数据归档失败：" + e.getMessage());
            }
            // 2. 上传 MinIO（流式，大小已知）
            String objectKey = NuscenesMetaSupport.META_OBJECT_PREFIX
                + request.getUploadId() + META_ARCHIVE_SUFFIX;
            minioService.putObject(objectKey,
                new ByteArrayInputStream(buffer.toByteArray()), buffer.size(), CONTENT_TYPE_ZIP);
            // 3. 登记归档记录
            LabelNuscenesMeta meta = new LabelNuscenesMeta();
            meta.setProjectId(request.getProjectId());
            meta.setBatchId(request.getBatchId());
            meta.setUploadId(request.getUploadId());
            meta.setVersionDir(versionDir);
            meta.setMetaObjectKey(objectKey);
            meta.setStatus(META_STATUS_ARCHIVED);
            meta.setUserId(request.getUserId());
            nuscenesMapper.insertMeta(meta);
            return meta;
        }
    }
}
