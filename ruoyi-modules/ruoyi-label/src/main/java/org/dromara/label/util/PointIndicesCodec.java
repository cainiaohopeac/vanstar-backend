package org.dromara.label.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 点云点索引（pointIndices）紧凑编解码 —— 与前端 utils/pointIndicesCodec.ts **严格对称**。
 *
 * 背景：分割点索引原样存 JSON 数组时，几十万点的实例会把 annotation_json 膨胀到几 MB~几十 MB，
 * UPDATE 写库耗时数十秒引发行锁超时。前端保存时已编码为 {enc:'drle-b64', n, data(base64)}，
 * 本类负责后端消费侧（导出/统计等）把编码格式还原为明文索引列表。
 *
 * 字节流布局（与前端一致）：
 *   varint n                    总点数
 *   每个游程两段 varint：gap（与上一点距离，首点为 首点+1，恒 >0）、lenMinus1（游程长度-1）
 *   游程内连续点固定 +1 推进，不占字节
 *
 * 兼容性：明文数组（旧数据）原样返回；null/空返回空列表。
 */
public final class PointIndicesCodec {

    /** 与前端 POINT_INDICES_ENC 一致的算法标识 */
    public static final String ENC = "drle-b64";

    private PointIndicesCodec() {
    }

    /** 是否为编码格式 */
    public static boolean isEncoded(Object v) {
        if (!(v instanceof Map<?, ?> m)) {
            return false;
        }
        return ENC.equals(m.get("enc"));
    }

    /**
     * 消费侧统一入口：编码对象 → 明文 List&lt;Integer&gt;（升序）；明文 List → 逐项转 Integer；
     * null/其它 → 空列表。解码遇损坏数据抛出的运行时异常在此吞掉并返回空列表
     * （导出宁可少一份实例，也不能让整包导出失败）。
     */
    public static List<Integer> decodeToPlain(Object v) {
        if (v == null) {
            return Collections.emptyList();
        }
        if (isEncoded(v)) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) v;
                Object data = m.get("data");
                Object nObj = m.get("n");
                if (!(data instanceof String s)) {
                    return Collections.emptyList();
                }
                int n = nObj instanceof Number num ? num.intValue() : 0;
                return decode(Base64.getDecoder().decode(s.getBytes(StandardCharsets.ISO_8859_1)), n);
            } catch (RuntimeException e) {
                return Collections.emptyList();
            }
        }
        if (v instanceof List<?> list) {
            List<Integer> out = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item instanceof Number num) {
                    out.add(num.intValue());
                }
            }
            return out;
        }
        return Collections.emptyList();
    }

    /** varint 游程解码核心（与前端 decodePointIndices 一致） */
    private static List<Integer> decode(byte[] bytes, int n) {
        List<Integer> out = new ArrayList<>(n > 0 ? n : 16);
        Pos pos = new Pos();
        long count = readVarint(bytes, pos);
        int cur = -1;
        while (out.size() < count) {
            int gap = readVarint(bytes, pos);
            int runLen = readVarint(bytes, pos) + 1;
            cur += gap;
            for (int k = 0; k < runLen && out.size() < count; k++) {
                out.add(cur + k);
            }
            cur += runLen - 1;
        }
        return out;
    }

    private static final class Pos {
        int i;
    }

    private static int readVarint(byte[] bytes, Pos pos) {
        int result = 0;
        int shift = 0;
        for (;;) {
            int b = bytes[pos.i++] & 0xff;
            result |= (b & 0x7f) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
            shift += 7;
        }
    }
}
