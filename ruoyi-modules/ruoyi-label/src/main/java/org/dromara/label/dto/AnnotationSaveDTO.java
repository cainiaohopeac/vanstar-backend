package org.dromara.label.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 保存标注请求（整份标注文档存档）
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnnotationSaveDTO implements Serializable {

    /** 当前标注资产ID */
    private Long activeAssetId;
    /** 当前条目文件名 */
    private String activeItem;
    /** 审核问题批注（可选） */
    private String comment;
    /**
     * 帧级全局属性值（可选）：图片标注页「全局属性」弹窗给当前帧填的属性值，
     * 属性 schema 来自 label_global_attribute（任务详情页「全局属性」tab 维护）。
     * 随整份 DTO 落入 annotation_json $.globalAttrs，打开工作台时从存档 JSON 取回。
     */
    private Map<String, Object> globalAttrs;
    /** 当前工具（点云可选） */
    private String tool;
    /** 标注对象集合 */
    private List<AnnotationObjectDTO> objects;
    /**
     * 对象轨迹集合（逐帧 / 关键帧插值，可选）。
     *
     * <p>为空表示本次请求不涉及逐帧（兼容老请求）。非空时按 trackToken 增量落库：
     * 存在即更新范围/空洞，不存在即新建；deleted=true 表示整条轨迹作废。</p>
     */
    private List<ObjectTrackDTO> tracks;
    /**
     * 编辑模式（新增）：
     *   labeler  = 标注员视角（默认；空值视同 labeler，兼容老请求）
     *   reviewer = 审核员修订视角（仅审核中/复审中状态、当前用户是该条 reviewer 时允许）
     *
     * <p>审核员修订模式仅写标注内容（label_annotation + label_annotation_object），
     * 不修改 label_asset.label_status / review_status / label_data_entry.* / label_job.status，
     * 避免把已领取状态冲掉。审计通过 {@code label_review_record.review_type='reviewer_edit'} 留痕。</p>
     */
    private String editMode;
    /**
     * 多帧批量保存（可选）：点云序列标注把「有对象的所有帧」一次性提交。
     *
     * <p>非空时服务端在一请求内逐帧落库——每帧写各自的 annotation header 与对象表，
     * 互不串扰；状态推进（提交审核时的条目/作业流转）只在最后执行一次。
     * 为空表示老的单帧请求，走原有逻辑，完全向后兼容。
     */
    private List<AnnotationFrameDTO> frames;
}
