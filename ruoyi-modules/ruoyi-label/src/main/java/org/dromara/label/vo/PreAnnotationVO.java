package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 单个数据资产的预标注结果 VO。
 */
@Data
public class PreAnnotationVO implements Serializable {

    private Long preAnnotationId;
    private Long assetId;
    /** 数据资产文件名 */
    private String fileName;
    /** 预标注来源（nuscenes） */
    private String source;
    /** 解析所用压缩包名 */
    private String sourceFile;
    /** nuScenes 样本Token */
    private String sampleToken;
    /** 坐标系所属传感器通道（如 LIDAR_TOP） */
    private String sensorChannel;
    private Integer boxCount;
    private String status;
    private LocalDateTime createTime;
    /** 预标注框列表 */
    private List<PreAnnotationObjectVO> objects;
}
