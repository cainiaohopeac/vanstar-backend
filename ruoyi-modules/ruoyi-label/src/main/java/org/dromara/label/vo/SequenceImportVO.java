package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * nuScenes 序列导入结果 VO
 */
@Data
public class SequenceImportVO implements Serializable {

    private int sequenceCount;
    private int sensorCount;
    private int frameCount;
    private int sensorDataCount;
}
