package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 序列标注视图 VO
 */
@Data
public class SequenceAnnotationVO implements Serializable {

    private SequenceVO sequence;
    private Long projectId;
    /** 帧列表（含传感器数据） */
    private List<SequenceFrameVO> frames;
    /** 项目类别引用 */
    private List<ClassRefVO> classes;
    /** 按帧的标注对象：key = frame-{frameId}，value = {"objects": [...]} */
    private Map<String, Object> annotations;
}
