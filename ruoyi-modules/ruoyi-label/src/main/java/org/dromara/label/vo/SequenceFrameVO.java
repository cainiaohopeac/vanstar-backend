package org.dromara.label.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

/**
 * 序列标注视图中携带传感器数据的帧 VO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SequenceFrameVO extends FrameVO implements Serializable {

    /** 帧内传感器数据 */
    private List<SensorDataVO> sensorData;
}
