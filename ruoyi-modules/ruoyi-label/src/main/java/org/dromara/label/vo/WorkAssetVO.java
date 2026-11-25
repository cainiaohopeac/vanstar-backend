package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 标注工作台资产 VO
 */
@Data
public class WorkAssetVO implements Serializable {

    private Long assetId;
    /** 数据资产所属的数据条目ID（数据集合包，工作台「放弃当前条目」用） */
    private Long entryId;
    /** 数据条目名称（数据集合包名称，工作台底部状态栏展示） */
    private String entryName;
    /** 数据条目编号（数据集合包编号，工作台底部状态栏展示） */
    private String entryNo;
    private String fileName;
    private String fileUrl;
    private String thumbnailUrl;
    private String mediaType;
    /** 作业内排序序号 */
    private Long sortOrder;
    /** 图像原生宽度（像素）：逐帧插值跨不同分辨率帧时用于归一化 */
    private Integer width;
    /** 图像原生高度（像素） */
    private Integer height;
    /** 帧有效性标记（normal 正常 / invalid 无效） */
    private String frameFlag;
    /** 无效原因 */
    private String invalidReason;
    /** 参考图片：点云作业中与当前 bin 同名（仅扩展名不同）的相机图 */
    private List<WorkAssetVO> refImages;
    /** 传感器标定：点云资产为 LIDAR 外参，参考图项为相机内外参（投影联动用） */
    private CalibVO calib;
    /** 该帧自车位姿平移 [x, y, z]（nuScenes ego_pose.translation）：点云全局坐标对齐用 */
    private List<Double> egoTranslation;
    /** 该帧自车位姿四元数 [w, x, y, z]（nuScenes ego_pose.rotation）：点云全局坐标对齐用 */
    private List<Double> egoRotation;

    // ===== 「数据明细」面板字段（工作台顶栏数据明细抽屉展示） =====
    /** 条目归属批次名称（无批次时为 null） */
    private String batchName;
    /** 条目标注状态（pending/labeling/...） */
    private String entryLabelStatus;
    /** 条目审核状态 */
    private String entryReviewStatus;
    /** 标注员姓名 */
    private String assigneeName;
    /** 审核员姓名 */
    private String reviewerName;
}
