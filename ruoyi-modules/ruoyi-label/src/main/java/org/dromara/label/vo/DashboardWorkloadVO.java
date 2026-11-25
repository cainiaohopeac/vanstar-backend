package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 看板人员工作量 VO
 */
@Data
public class DashboardWorkloadVO implements Serializable {

    private String name;
    /** 累计标注量 */
    private Long total;
    /** 今日标注量 */
    private Long today;
    /** 本周标注量 */
    private Long week;
    /** 审核量 */
    private Long review;
    /** 框标注量（2D 矩形 bbox/rect + 3D 立体框 cuboid） */
    private Long boxCount;
    /** 点标注量（关键点 point/keypoint） */
    private Long pointCount;
    /** 处理帧量（本人作业内去重后的数据 / 帧 / 图片数） */
    private Long frameCount;
    /** 处理条目量（本人作业覆盖到的去重条目数） */
    private Long entryCount;
}
