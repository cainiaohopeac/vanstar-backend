package org.dromara.label.vo;

import java.io.Serializable;
import java.util.Map;
import lombok.Data;

/**
 * 成员标签/帧产出（标签工作量柱状图、帧工作量表格共用）。
 */
@Data
public class StatsMemberOutputVO implements Serializable {

    private Long memberId;

    private String memberName;

    /** 标签（标注对象）总数。 */
    private Long tagCount;

    /** 处理过的统计单元总数：图片/文本项目按资产，序列项目按帧。 */
    private Long totalFrames;

    /** 有标签单元数（至少 1 个标注对象）。 */
    private Long labeledFrames;

    /** 无标签单元数（处理过但产出为空，负样本）。 */
    private Long emptyFrames;

    /** 单元平均标签数，保留两位小数。 */
    private Double tagPerFrame;

    /**
     * 按几何类别汇总的标签数（仅成员标签工作量接口返回）：
     * point=点 / line=线 / box2d=2D框 / seg2d=2D分割框 / box3d=3D框 / seg3d=3D分割。
     */
    private Map<String, Long> kinds;
}
