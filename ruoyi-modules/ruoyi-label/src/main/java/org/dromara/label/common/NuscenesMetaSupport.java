package org.dromara.label.common;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * nuScenes 元数据共享工具：zip 条目识别、文件名匹配键、元数据索引构建与坐标变换数学。
 *
 * <p>预标注（global -&gt; sensor 正变换）、导入建映射、导出（sensor -&gt; global 逆变换）
 * 三处共用同一份坐标语义实现，避免多处维护导致不一致。</p>
 */
public final class NuscenesMetaSupport {

    private NuscenesMetaSupport() {
    }

    // ---------------- 共享常量（消除魔法值） ----------------

    /** 解析预标注所必需的 nuScenes 元数据文件（缺少则无法预标注） */
    public static final List<String> REQUIRED_JSON = List.of(
        "sample_data", "sample_annotation", "category", "ego_pose", "calibrated_sensor", "sensor");

    /** nuScenes 元数据文件全集（白名单外的 scene/log/map 等原样归档但不参与解析） */
    public static final List<String> ACCEPTED_JSON = List.of(
        "sample", "sample_data", "sample_annotation", "category", "instance",
        "attribute", "ego_pose", "calibrated_sensor", "sensor", "visibility");

    /** 重写导出时需要按 sample 分组的元数据表名 */
    public static final String TABLE_SAMPLE_ANNOTATION = "sample_annotation";

    /** 点云传感器通道前缀 */
    public static final List<String> POINT_CLOUD_CHANNEL_PREFIX = List.of("LIDAR", "RADAR");

    /** 相机传感器通道前缀（bin 与相机参考图关联用） */
    public static final List<String> CAMERA_CHANNEL_PREFIX = List.of("CAM");

    /** 需要剥离的常见数据文件扩展名（nuScenes 点云存在 .bin 与 .pcd.bin 两种写法） */
    public static final List<String> DATA_EXTENSIONS = List.of(".bin", ".pcd", ".jpg", ".jpeg", ".png", ".bmp");

    /** nuScenes 版本目录前缀（v1.0-mini / v1.0-trainval 等） */
    public static final String VERSION_DIR_PREFIX = "v1.0-";

    /** nuScenes 数据文件的 samples 目录前缀 */
    public static final String SAMPLES_DIR_PREFIX = "samples/";

    /** nuScenes 元数据归档在 MinIO 中的对象前缀 */
    public static final String META_OBJECT_PREFIX = "uploads/nuscenes/meta/";

    // ---------------- 实例运动状态（由 attribute.json 推导） ----------------

    public static final String MOTION_MOVING = "moving";
    public static final String MOTION_STATIC = "static";
    public static final String MOTION_UNKNOWN = "unknown";

    // ---------------- zip 条目识别 ----------------

    /** 判定 zip 条目是否 nuScenes 元数据 JSON（位于 v1.0-* 目录下）。 */
    public static boolean isMetaJsonEntry(String entryName) {
        String path = normalizePath(entryName);
        if (!path.endsWith(".json")) {
            return false;
        }
        int slash = path.lastIndexOf('/');
        String parent = slash >= 0 ? path.substring(0, slash) : "";
        return parent.contains(VERSION_DIR_PREFIX);
    }

    /** 取 zip 条目的 json 表名（basename 去 .json，小写）；非 json 返回空。 */
    public static String jsonKeyOf(String entryName) {
        String path = normalizePath(entryName);
        String base = path.substring(path.lastIndexOf('/') + 1);
        if (!base.endsWith(".json")) {
            return "";
        }
        return base.substring(0, base.length() - ".json".length());
    }

    /** 取 zip 条目所属的 nuScenes 版本目录（v1.0-mini 等）；不存在返回空。 */
    public static String versionDirOf(String entryName) {
        String path = normalizePath(entryName);
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash <= 0) {
            return "";
        }
        String parent = path.substring(0, lastSlash);
        int parentSlash = parent.lastIndexOf('/');
        String tailDir = parentSlash >= 0 ? parent.substring(parentSlash + 1) : parent;
        return tailDir.startsWith(VERSION_DIR_PREFIX) ? tailDir : "";
    }

    /** 路径归一化：反斜杠转正斜杠并小写。 */
    private static String normalizePath(String entryName) {
        return entryName == null ? "" : entryName.replace('\\', '/').toLowerCase(Locale.ROOT);
    }

    /** 取文件名去扩展名后的匹配键（如 n008-...__LIDAR_TOP__1533151603547590）。 */
    public static String dataFileKey(String pathOrName) {
        String text = pathOrName == null ? "" : pathOrName.trim().replace('\\', '/');
        if (text.isEmpty()) {
            return "";
        }
        String base = text.substring(text.lastIndexOf('/') + 1);
        String lower = base.toLowerCase(Locale.ROOT);
        for (String ext : DATA_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                base = base.substring(0, base.length() - ext.length());
                lower = base.toLowerCase(Locale.ROOT);
            }
        }
        return base.toLowerCase(Locale.ROOT);
    }

    /**
     * 取保留目录结构的数据路径键（如 samples/cam_front/xxx）。
     * <p>
     * 背景：官方 nuScenes 文件名全局唯一，{@link #dataFileKey} 的纯文件名键即可匹配；
     * 但自采数据集常出现「同一帧各传感器文件名完全相同、仅目录（通道）不同」的命名
     * （如 samples/CAM_FRONT/xxx.jpg 与 samples/CAM_BACK/xxx.jpg 同名），
     * 纯文件名键会把不同通道混为同一资产——必须带目录才能区分。
     * <p>
     * 键规则：定位 nuScenes 数据根（samples/ 或 sweeps/，兼容元数据 filename 与
     * 平台 file_url 两种入参），取从根开始的完整相对路径并去扩展名小写；
     * 找不到数据根（非 nuScenes 目录结构）时退化为纯文件名键，保证两侧键一致。
     */
    public static String dataPathKey(String pathOrUrl) {
        String text = pathOrUrl == null ? "" : pathOrUrl.trim().replace('\\', '/');
        if (text.isEmpty()) {
            return "";
        }
        int root = dataRootIndex(text);
        if (root < 0) {
            return dataFileKey(text);
        }
        String base = text.substring(root);
        String lower = base.toLowerCase(Locale.ROOT);
        for (String ext : DATA_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                base = base.substring(0, base.length() - ext.length());
                lower = base.toLowerCase(Locale.ROOT);
            }
        }
        return lower;
    }

    /** 定位数据根（samples/ 或 sweeps/）在路径中的起始下标，须为路径段边界；未找到返回 -1。 */
    private static int dataRootIndex(String text) {
        int best = -1;
        best = minRootIndex(text, "samples/", best);
        best = minRootIndex(text, "sweeps/", best);
        return best;
    }

    private static int minRootIndex(String text, String root, int best) {
        int idx = text.indexOf(root);
        while (idx >= 0) {
            if (idx == 0 || text.charAt(idx - 1) == '/') {
                return best < 0 || idx < best ? idx : best;
            }
            idx = text.indexOf(root, idx + 1);
        }
        return best;
    }

    // ---------------- 元数据索引构建 ----------------

    /**
     * 构建 nuScenes 元数据索引。
     * <p>逻辑：
     * 1. 建立 token 索引：sensor(通道) / calibrated_sensor / ego_pose / category / visibility；
     * 2. sample_annotation 按 sample_token 分组；
     * 3. sample_data 建立三种索引：按 token、按 sample 分组、点云文件名键 -&gt; 行。
     * </p>
     */
    public static NuscenesIndex buildIndex(Map<String, List<Map<String, Object>>> raw) {
        NuscenesIndex index = new NuscenesIndex();
        // 1. token 索引
        for (Map<String, Object> row : listOf(raw, "sensor")) {
            String token = text(row.get("token"));
            if (!token.isEmpty()) {
                index.sensorChannelByToken.put(token, text(row.get("channel")));
            }
        }
        for (Map<String, Object> row : listOf(raw, "calibrated_sensor")) {
            String token = text(row.get("token"));
            if (!token.isEmpty()) {
                index.calibratedSensorByToken.put(token, row);
            }
        }
        for (Map<String, Object> row : listOf(raw, "ego_pose")) {
            String token = text(row.get("token"));
            if (!token.isEmpty()) {
                index.egoPoseByToken.put(token, row);
            }
        }
        for (Map<String, Object> row : listOf(raw, "category")) {
            String token = text(row.get("token"));
            if (!token.isEmpty()) {
                String categoryName = text(row.get("name"));
                index.categoryNameByToken.put(token, categoryName);
                index.categoryTokenByName.putIfAbsent(categoryName.toLowerCase(Locale.ROOT), token);
            }
        }
        for (Map<String, Object> row : listOf(raw, "visibility")) {
            String token = text(row.get("token"));
            if (!token.isEmpty()) {
                index.visibilityLevelByToken.put(token, text(row.get("level")));
            }
        }
        // attribute / instance / sample：官方表里带着「运动状态」「实例帧范围」「帧顺序」，
        // 之前没解析导致 nuScenes 的 instance.property（静止目标）与漏标检查无从下手
        for (Map<String, Object> row : listOf(raw, "attribute")) {
            String token = text(row.get("token"));
            if (!token.isEmpty()) {
                index.attributeNameByToken.put(token, text(row.get("name")));
            }
        }
        for (Map<String, Object> row : listOf(raw, "instance")) {
            String token = text(row.get("token"));
            if (!token.isEmpty()) {
                index.instanceByToken.put(token, row);
            }
        }
        for (Map<String, Object> row : listOf(raw, "sample")) {
            String token = text(row.get("token"));
            if (!token.isEmpty()) {
                index.sampleByToken.put(token, row);
            }
        }
        // 2. 标注框按样本分组（顺带建 token / instance 索引）
        for (Map<String, Object> row : listOf(raw, TABLE_SAMPLE_ANNOTATION)) {
            String annoToken = text(row.get("token"));
            if (!annoToken.isEmpty()) {
                index.annotationByToken.putIfAbsent(annoToken, row);
            }
            String sampleToken = text(row.get("sample_token"));
            if (!sampleToken.isEmpty()) {
                index.annotationsBySample.computeIfAbsent(sampleToken, k -> new ArrayList<>()).add(row);
            }
            String instanceToken = text(row.get("instance_token"));
            if (!instanceToken.isEmpty()) {
                index.annotationsByInstance.computeIfAbsent(instanceToken, k -> new ArrayList<>()).add(row);
            }
        }
        // 帧顺序：按 scene 的 prev/next 链排出的帧序号（缺10帧 / 漏标检查的基础）
        index.frameIndexBySampleToken = buildFrameIndex(index);
        // 3. sample_data 三重索引（token / 样本分组 / 点云文件名）
        for (Map<String, Object> row : listOf(raw, "sample_data")) {
            String sampleToken = text(row.get("sample_token"));
            if (sampleToken.isEmpty()) {
                continue;
            }
            String token = text(row.get("token"));
            if (!token.isEmpty()) {
                index.sampleDataByToken.putIfAbsent(token, row);
            }
            index.sampleDataBySample.computeIfAbsent(sampleToken, k -> new ArrayList<>()).add(row);
            String fileKey = dataFileKey(text(row.get("filename")));
            if (fileKey.isEmpty() || !isPointCloudSampleData(row, index)) {
                continue;
            }
            row.put("_sampleToken", sampleToken);
            row.put("_fileKey", fileKey);
            // 双键索引：路径键（含通道目录）优先，兼容「同帧各传感器同名」的自采数据集；
            // 纯文件名键兜底（官方数据集文件名唯一，两键等价），putIfAbsent 保证同键首行胜出。
            index.sampleDataByFileKey.putIfAbsent(dataPathKey(text(row.get("filename"))), row);
            index.sampleDataByFileKey.putIfAbsent(fileKey, row);
        }
        return index;
    }

    /** 解析 sample_data 的传感器通道。 */
    public static String sensorChannelOf(Map<String, Object> sampleData, NuscenesIndex index) {
        Map<String, Object> calib = index.calibratedSensorByToken.get(text(sampleData.get("calibrated_sensor_token")));
        if (calib == null) {
            return "";
        }
        String channel = index.sensorChannelByToken.get(text(calib.get("sensor_token")));
        return channel == null ? "" : channel;
    }

    /** 判断 sample_data 是否来自点云传感器（LIDAR/RADAR）。 */
    public static boolean isPointCloudSampleData(Map<String, Object> sampleData, NuscenesIndex index) {
        return isChannelOf(sampleData, index, POINT_CLOUD_CHANNEL_PREFIX);
    }

    /** 判断 sample_data 是否来自相机传感器（CAM_*）。 */
    public static boolean isCameraSampleData(Map<String, Object> sampleData, NuscenesIndex index) {
        return isChannelOf(sampleData, index, CAMERA_CHANNEL_PREFIX);
    }

    private static boolean isChannelOf(Map<String, Object> sampleData, NuscenesIndex index, List<String> prefixes) {
        String channel = sensorChannelOf(sampleData, index);
        for (String prefix : prefixes) {
            if (channel.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    // ---------------- 实例属性 / 实例链 / 帧序 ----------------

    /**
     * 排出每个 scene 内的帧序号：sample token -&gt; 帧序号（从 1 开始）。
     * <p>优先走 sample.prev / next 链（nuScenes 的帧时序就是这条链）；
     * 链有断（自采数据集常见）时按 timestamp 兜底补进去，保证不丢帧。</p>
     */
    public static Map<String, Integer> buildFrameIndex(NuscenesIndex index) {
        Map<String, Integer> frameIndex = new HashMap<>();
        Map<String, List<Map<String, Object>>> byScene = new HashMap<>();
        for (Map<String, Object> row : index.sampleByToken.values()) {
            byScene.computeIfAbsent(text(row.get("scene_token")), k -> new ArrayList<>()).add(row);
        }
        for (List<Map<String, Object>> rows : byScene.values()) {
            List<Map<String, Object>> ordered = orderByChain(rows);
            for (int i = 0; i < ordered.size(); i++) {
                frameIndex.put(text(ordered.get(i).get("token")), i + 1);
            }
        }
        return frameIndex;
    }

    /** prev/next 链排序：找链头（prev 为空或不在本组）后依次走 next，链外的按 timestamp 追加。 */
    private static List<Map<String, Object>> orderByChain(List<Map<String, Object>> rows) {
        if (rows.size() <= 1) {
            return new ArrayList<>(rows);
        }
        Map<String, Map<String, Object>> byToken = new HashMap<>();
        for (Map<String, Object> row : rows) {
            byToken.put(text(row.get("token")), row);
        }
        Set<String> hasPrev = new HashSet<>();
        for (Map<String, Object> row : rows) {
            String prev = text(row.get("prev"));
            if (!prev.isEmpty()) {
                hasPrev.add(prev);
            }
        }
        List<Map<String, Object>> heads = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (!hasPrev.contains(text(row.get("token")))) {
                heads.add(row);
            }
        }
        heads.sort(Comparator.comparingLong(row -> (long) num(row.get("timestamp"))));

        List<Map<String, Object>> ordered = new ArrayList<>(rows.size());
        Set<String> visited = new HashSet<>();
        for (Map<String, Object> head : heads) {
            Map<String, Object> cursor = head;
            while (cursor != null && visited.add(text(cursor.get("token")))) {
                ordered.add(cursor);
                String next = text(cursor.get("next"));
                cursor = next.isEmpty() ? null : byToken.get(next);
            }
        }
        List<Map<String, Object>> rest = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (!visited.contains(text(row.get("token")))) {
                rest.add(row);
            }
        }
        rest.sort(Comparator.comparingLong(row -> (long) num(row.get("timestamp"))));
        ordered.addAll(rest);
        return ordered;
    }

    /** 某实例的标注按 prev/next 链排成帧序；实例只有一帧或没有链信息时原样返回。 */
    public static List<Map<String, Object>> orderedInstanceAnnotations(NuscenesIndex index, String instanceToken) {
        List<Map<String, Object>> rows = index.annotationsByInstance.get(instanceToken);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        if (rows.size() == 1) {
            return new ArrayList<>(rows);
        }
        List<Map<String, Object>> ordered = orderByChain(rows);
        return ordered.isEmpty() ? new ArrayList<>(rows) : ordered;
    }

    /** 标注的 attribute_tokens -&gt; attribute.json 的属性名列表。 */
    public static List<String> attributeNamesOf(NuscenesIndex index, Map<String, Object> annotation) {
        List<String> names = new ArrayList<>();
        if (!(annotation.get("attribute_tokens") instanceof List<?> tokens)) {
            return names;
        }
        for (Object token : tokens) {
            String name = index.attributeNameByToken.get(text(token));
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * 属性名推导运动状态：moving / static / unknown。
     * nuScenes 里 vehicle.moving = 运动 vehicle.parked / pedestrian.standing / stopped 等为静止。
     * cycle.with_rider 有骑手，按运动算。
     */
    public static String motionStatusOf(List<String> attributeNames) {
        for (String name : attributeNames) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.endsWith("moving") || lower.contains("with_rider")) {
                return MOTION_MOVING;
            }
        }
        for (String name : attributeNames) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.contains("parked") || lower.contains("stopped") || lower.contains("standing")
                || lower.contains("sitting") || lower.contains("lying") || lower.contains("static")) {
                return MOTION_STATIC;
            }
        }
        return MOTION_UNKNOWN;
    }

    /** 该 sample 在所属 scene 中的帧序号（从 1 开始）；无 sample 表时返回 -1。 */
    public static int frameIndexOf(NuscenesIndex index, String sampleToken) {
        Integer value = index.frameIndexBySampleToken.get(sampleToken);
        return value == null ? -1 : value;
    }

    // ---------------- 坐标变换数学 ----------------

    public static List<Map<String, Object>> listOf(Map<String, List<Map<String, Object>>> raw, String key) {
        List<Map<String, Object>> value = raw.get(key);
        return value == null ? List.of() : value;
    }

    public static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public static double num(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return 0d;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0d;
        }
    }

    public static double[] vec3(Object value) {
        if (!(value instanceof List<?> list) || list.size() < 3) {
            return new double[]{0d, 0d, 0d};
        }
        return new double[]{num(list.get(0)), num(list.get(1)), num(list.get(2))};
    }

    /** nuScenes 四元数格式为 [w, x, y, z]。 */
    public static double[] quat(Object value) {
        if (!(value instanceof List<?> list) || list.size() < 4) {
            return new double[]{1d, 0d, 0d, 0d};
        }
        return new double[]{num(list.get(0)), num(list.get(1)), num(list.get(2)), num(list.get(3))};
    }

    /** 四元数转 3x3 旋转矩阵（行主序），表示 frame -&gt; global 的旋转。 */
    public static double[] quatToMatrix(double[] q) {
        double w = q[0];
        double x = q[1];
        double y = q[2];
        double z = q[3];
        return new double[]{
            1 - 2 * (y * y + z * z), 2 * (x * y - w * z), 2 * (x * z + w * y),
            2 * (x * y + w * z), 1 - 2 * (x * x + z * z), 2 * (y * z - w * x),
            2 * (x * z - w * y), 2 * (y * z + w * x), 1 - 2 * (x * x + y * y)
        };
    }

    /** 旋转矩阵（行主序）转 nuScenes 四元数 [w, x, y, z]（与 quatToMatrix 互逆）。 */
    public static double[] matrixToQuat(double[] m) {
        double trace = m[0] + m[4] + m[8];
        double w;
        double x;
        double y;
        double z;
        if (trace > 0) {
            double s = Math.sqrt(trace + 1.0) * 2;
            w = 0.25 * s;
            x = (m[7] - m[5]) / s;
            y = (m[2] - m[6]) / s;
            z = (m[3] - m[1]) / s;
        } else if (m[0] > m[4] && m[0] > m[8]) {
            double s = Math.sqrt(1.0 + m[0] - m[4] - m[8]) * 2;
            w = (m[7] - m[5]) / s;
            x = 0.25 * s;
            y = (m[1] + m[3]) / s;
            z = (m[2] + m[6]) / s;
        } else if (m[4] > m[8]) {
            double s = Math.sqrt(1.0 + m[4] - m[0] - m[8]) * 2;
            w = (m[2] - m[6]) / s;
            x = (m[1] + m[3]) / s;
            y = 0.25 * s;
            z = (m[5] + m[7]) / s;
        } else {
            double s = Math.sqrt(1.0 + m[8] - m[0] - m[4]) * 2;
            w = (m[3] - m[1]) / s;
            x = (m[2] + m[6]) / s;
            y = (m[5] + m[7]) / s;
            z = 0.25 * s;
        }
        return new double[]{w, x, y, z};
    }

    /** 绕 Z 轴（yaw，弧度）的 3x3 旋转矩阵（行主序）。 */
    public static double[] yawToMatrix(double yaw) {
        double c = Math.cos(yaw);
        double s = Math.sin(yaw);
        return new double[]{c, -s, 0, s, c, 0, 0, 0, 1};
    }

    /** 矩阵转置。 */
    public static double[] transpose(double[] m) {
        return new double[]{
            m[0], m[3], m[6],
            m[1], m[4], m[7],
            m[2], m[5], m[8]
        };
    }

    /** 矩阵乘法 a·b。 */
    public static double[] matMul(double[] a, double[] b) {
        double[] result = new double[9];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                double sum = 0d;
                for (int k = 0; k < 3; k++) {
                    sum += a[row * 3 + k] * b[k * 3 + col];
                }
                result[row * 3 + col] = sum;
            }
        }
        return result;
    }

    /** 矩阵乘向量（m·v）。 */
    public static double[] matVec(double[] m, double[] v) {
        return new double[]{
            m[0] * v[0] + m[1] * v[1] + m[2] * v[2],
            m[3] * v[0] + m[4] * v[1] + m[5] * v[2],
            m[6] * v[0] + m[7] * v[1] + m[8] * v[2]
        };
    }

    /** 用矩阵的转置变换向量（m^T·v），即坐标系的逆旋转。 */
    public static double[] matVecT(double[] m, double[] v) {
        return new double[]{
            m[0] * v[0] + m[3] * v[1] + m[6] * v[2],
            m[1] * v[0] + m[4] * v[1] + m[7] * v[2],
            m[2] * v[0] + m[5] * v[1] + m[8] * v[2]
        };
    }

    public static double[] sub(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    public static double[] add(double[] a, double[] b) {
        return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    /** 数值四舍五入到万分位（与预标注入库精度一致）。 */
    public static double round4(double value) {
        return Math.round(value * 10000d) / 10000d;
    }

    /**
     * nuScenes 元数据索引（字段公开，供预标注 / 映射构建 / 导出共用）。
     * <p>内部以 Map&lt;String, Object&gt; 承载 nuScenes JSON 行——该结构是 JSON 数据本身
     * 的动态形态，仅在服务实现内部流转，不进入任何接口出入参。</p>
     */
    public static final class NuscenesIndex {
        /** sensor token -&gt; 通道名 */
        public final Map<String, String> sensorChannelByToken = new HashMap<>();
        /** calibrated_sensor token -&gt; 行 */
        public final Map<String, Map<String, Object>> calibratedSensorByToken = new HashMap<>();
        /** ego_pose token -&gt; 行 */
        public final Map<String, Map<String, Object>> egoPoseByToken = new HashMap<>();
        /** category token -&gt; 类别名 */
        public final Map<String, String> categoryNameByToken = new HashMap<>();
        /** 类别名 -&gt; category token（导出反查用） */
        public final Map<String, String> categoryTokenByName = new HashMap<>();
        /** visibility token -&gt; level */
        public final Map<String, String> visibilityLevelByToken = new HashMap<>();
        /** sample_token -&gt; sample_annotation 行列表 */
        public final Map<String, List<Map<String, Object>>> annotationsBySample = new HashMap<>();
        /** 点云 sample_data 文件名键 -&gt; 行（含 _sampleToken/_fileKey） */
        public final Map<String, Map<String, Object>> sampleDataByFileKey = new HashMap<>();
        /** sample_data token -&gt; 行（导出逆变换定位 ego/calib 用） */
        public final Map<String, Map<String, Object>> sampleDataByToken = new HashMap<>();
        /** sample_token -&gt; sample_data 行列表（同帧多传感器） */
        public final Map<String, List<Map<String, Object>>> sampleDataBySample = new HashMap<>();
        /** attribute token -&gt; 属性名（如 vehicle.moving） */
        public final Map<String, String> attributeNameByToken = new HashMap<>();
        /** instance token -&gt; instance.json 行（含 nbr_annotations / first / last annotation token） */
        public final Map<String, Map<String, Object>> instanceByToken = new HashMap<>();
        /** sample token -&gt; sample.json 行（含 scene_token / prev / next / timestamp） */
        public final Map<String, Map<String, Object>> sampleByToken = new HashMap<>();
        /** sample_annotation token -&gt; 行（实例 prev/next 链遍历用） */
        public final Map<String, Map<String, Object>> annotationByToken = new HashMap<>();
        /** instance_token -&gt; 该实例的标注行列表（未排序，取帧序用 orderedInstanceAnnotations） */
        public final Map<String, List<Map<String, Object>>> annotationsByInstance = new HashMap<>();
        /** sample token -&gt; 所属 scene 内的帧序号（从 1 开始）；没有 sample 表时为空 */
        public Map<String, Integer> frameIndexBySampleToken = new HashMap<>();
    }
}
