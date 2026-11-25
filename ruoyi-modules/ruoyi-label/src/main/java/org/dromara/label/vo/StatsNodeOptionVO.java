package org.dromara.label.vo;

import java.io.Serializable;
import lombok.Data;

/**
 * 工作流节点下拉项（节点选择器）。
 */
@Data
public class StatsNodeOptionVO implements Serializable {

    private String nodeKey;

    private String nodeName;

    /** start / label / review / end。 */
    private String nodeType;
}
