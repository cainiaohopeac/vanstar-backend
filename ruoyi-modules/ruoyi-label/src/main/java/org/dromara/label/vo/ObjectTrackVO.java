package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 对象轨迹 VO（逐帧 / 关键帧插值）
 *
 * <p>一条轨迹代表「跨帧的同一个目标」。关键帧几何落在 label_annotation_object
 * （带同一 track_token），这里只承载存在范围与逐帧控制元数据：</p>
 * <ul>
 *   <li>{@code firstAssetId} / {@code lastAssetId}：存在范围 [起, 止]（按条目资产顺序）。
 *       lastAssetId 为空表示延续到帧序列末帧，叠加无后续关键帧时即为「末段固定」。</li>
 *   <li>{@code holesJson}：单帧删除集合（资产ID数组），仅该帧不显示，前后帧不受影响。</li>
 *   <li>{@code interpolation}：插值方式，当前仅 linear（位置与长宽均匀变化）。</li>
 * </ul>
 */
@Data
public class ObjectTrackVO implements Serializable {

    private String trackToken;
    private Long entryId;
    private Long classId;
    /** 存在范围起始帧（资产ID） */
    private Long firstAssetId;
    /** 存在范围结束帧（资产ID）；NULL=延续到末帧 */
    private Long lastAssetId;
    /** 单帧删除集合 JSON 文本 */
    private String holesJson;
    /** 虚框区间集合 JSON 文本：[{from,to,dir}]，from/to 为资产ID，dir=fwd/bwd */
    private String gapsJson;
    /** 插值方式：linear（默认） */
    private String interpolation;
}
