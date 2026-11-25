package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 预标注执行结果 VO（一次预标注任务的统计）。
 */
@Data
public class PreAnnotationResultVO implements Serializable {

    private Long uploadId;
    /** 解析所用压缩包名 */
    private String sourceFile;
    /** 作用范围内的数据资产总数 */
    private Integer assetTotal;
    /** 成功匹配到 nuScenes 帧并生成预标注的资产数 */
    private Integer matchedAsset;
    /** 生成的预标注框总数 */
    private Integer boxTotal;
    /** 命中项目标签类别的框数 */
    private Integer matchedClassObject;
    /** 未命中项目标签类别的框数 */
    private Integer unmatchedClassObject;
    /** nuScenes 实例总数（按 instance_token 去重） */
    private Integer instanceCount;
    /** 存在漏标帧的实例数（实例链首末帧之间有没打的帧） */
    private Integer gapInstanceCount;
    /** 漏标帧总数（各实例缺帧之和，同一个实例只算一次） */
    private Integer missingFrameTotal;
    /**
     * 源格式未提供类别定义（如 YOLO 包里没有 class.json / classes.txt / data.yaml）。
     * 框已正常导入，但都没有类别名、全部匹配不上，需要标注员手工指定标签。
     */
    private Boolean classDefinitionMissing;
    /** 项目里原本没有、本次自动补建的标签数（源格式自带类别定义时才会发生） */
    private Integer createdClassCount;
    /** 自动补建的标签名，用于结果提示——项目被动了什么要让操作者看见 */
    private List<String> createdClassNames;
}
