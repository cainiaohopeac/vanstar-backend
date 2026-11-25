package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 传感器 VO
 */
@Data
public class SensorVO implements Serializable {

    private Long id;
    private Long projectId;
    private String channel;
    private String modality;
}
