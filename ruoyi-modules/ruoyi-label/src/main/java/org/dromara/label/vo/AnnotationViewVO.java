package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 标注工作台 VO（作业标注取数）
 */
@Data
public class AnnotationViewVO implements Serializable {

    private Long jobId;
    /** 作业信息（含 taskId/jobNo/progress/dataType 等） */
    private JobVO job;
    /** 任务/批次概要（「数据明细」面板：任务名称、任务类型、批次名称、条目数量、条目类型） */
    private JobTaskInfoVO taskInfo;
    /** 数据类型（image/point_cloud/...） */
    private String mediaType;
    private List<WorkAssetVO> assets;
    private WorkAssetVO activeAsset;
    private List<WorkClassVO> classes;
    private List<WorkObjectVO> objects;
    /**
     * 对象轨迹（逐帧 / 关键帧插值）。
     *
     * <p>每条轨迹描述一个跨帧目标的存在范围与逐帧控制信息；
     * 具体几何只存关键帧（随 objects 返回，带 trackToken），
     * 中间帧由前端按 track 插值渲染。</p>
     */
    private List<ObjectTrackVO> tracks;
    /** 任务是否开启预标注引入（开启时前端把 preAnnotationObjects 注入画布：可删改、首次保存转正） */
    private Boolean preAnnotationEnabled;
    /** 预标注对象（仅任务开启引入且资产尚无正式标注时返回，geometry 与正式标注同构） */
    private List<WorkObjectVO> preAnnotationObjects;
    /**
     * 整图批注（标注员填写的「问题批注」）。
     * 保存时随 AnnotationSaveDTO 整包落入 annotation_json，此前未回读导致标注员
     * 填完保存后再进入看不到自己写的内容，这里从存档 JSON 中取回。
     */
    private String comment;

    /**
     * 帧级全局属性值：当前激活资产最近一次存档的 {@code annotation_json $.globalAttrs}，
     * 供工作台「全局属性」弹窗回显（属性 schema 见 GET /global-attributes）。
     */
    private Object globalAttrs;

    /**
     * 审核节点可选的批注类型（错误内容）：漏点 / 漏标 / 不贴合 / 混点 / 类别错误或项目自定义。
     * 取自「任务当前节点」所属项目的审核节点配置，供批注弹窗的下拉框使用。
     */
    private List<String> commentTypes;
}
