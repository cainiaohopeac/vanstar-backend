package org.dromara.label.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 保存请求中的对象轨迹（逐帧 / 关键帧插值）
 *
 * <p>与 {@link org.dromara.label.vo.ObjectTrackVO} 语义一致。
 * 前端在开启逐帧后，针对每个跨帧目标维护一条轨迹：</p>
 * <ul>
 *   <li>画框 → 新建轨迹，范围 [画框帧, 末帧]，并把该帧写成一个关键帧。</li>
 *   <li>修改中间帧 → 该帧落实为关键帧，几何入库，插值自动覆盖两侧。</li>
 *   <li>从下一帧消失 / 取消连续 → 把 lastAssetId 收紧到该帧（前向截断）。</li>
 *   <li>删除此帧 → 把该帧加入 holes，其余帧不受影响。</li>
 * </ul>
 */
@Data
public class ObjectTrackDTO implements Serializable {

    private String trackToken;
    private Long entryId;
    private Long classId;
    /** 存在范围起始帧（资产ID） */
    private Long firstAssetId;
    /** 存在范围结束帧（资产ID）；NULL=延续到末帧 */
    private Long lastAssetId;
    /** 单帧删除集合（资产ID列表） */
    private List<Long> holes;
    /** 虚框区间集合（实框转虚框、截止到最近关键帧）：[{from,to,dir}]，from/to 为资产ID，dir=fwd/bwd */
    private List<Map<String, Object>> gaps;
    /** 插值方式：linear（默认） */
    private String interpolation;
    /** 被删除/作废的轨迹（业务上整条移除） */
    private Boolean deleted;
}
