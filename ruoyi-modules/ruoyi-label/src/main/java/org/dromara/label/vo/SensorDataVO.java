package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 帧传感器数据 VO
 */
@Data
public class SensorDataVO implements Serializable {

    private Long id;
    private Long frameId;
    private Long sensorId;
    private Long calibrationId;
    private Long egoPoseId;
    private String fileName;
    private String fileUrl;
    private String fileFormat;
    private Integer width;
    private Integer height;
    private Integer isKeyFrame;
    /** 传感器通道（LIDAR_TOP/CAM_FRONT/...） */
    private String channel;
    /** 传感器模态（lidar/camera/radar/...） */
    private String modality;
}
