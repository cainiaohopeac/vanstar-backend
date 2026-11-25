package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 帧 VO
 */
@Data
public class FrameVO implements Serializable {

    private Long id;
    private Long sequenceId;
    private String frameToken;
    private Integer frameIndex;
    private Long timestampUs;
    private Long prevFrameId;
    private Long nextFrameId;
    private Integer isKeyFrame;
}
