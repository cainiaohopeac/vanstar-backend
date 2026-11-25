package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 标注工作台已保存标注对象 VO
 */
@Data
public class WorkObjectVO implements Serializable {

    private String objectKey;
    private String type;
    private Long classId;
    private String className;
    private String color;
    /** 分组键（同组对象前端联动高亮 / 批量操作，空表示未分组） */
    private String groupKey;
    /**
     * 轨迹 Token：跨帧同一目标的标识（逐帧 / 关键帧插值使用）。
     *
     * <p>非空表示该对象是某条轨迹的关键帧，中间帧不落库、由前端插值渲染；
     * 为空表示「每帧独立」的传统对象（历史数据兼容，不参与插值）。</p>
     */
    private String trackToken;
    /** 对象归属 assetId（点云多 bin 场景下用于前端按条目隔离显示） */
    private Long assetId;
    /** 几何 JSON 文本 */
    private String geometryJson;
    /** 属性 JSON 文本 */
    private String attrJson;
    /** 点云实例点数 */
    private Integer pointCount;
}
