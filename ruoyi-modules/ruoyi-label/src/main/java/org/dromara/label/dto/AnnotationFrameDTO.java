package org.dromara.label.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 单帧标注（多帧批量保存时使用）
 *
 * <p>点云序列标注一次会产生很多帧的标注（含关键帧插值生成的中间帧）。
 * 若逐帧调用 {@code /jobs/{id}/save-draft}，帧数一多就会产生几十上百个请求，
 * 且并发提交容易出现部分失败。批量保存时前端把各帧按本结构放进
 * {@code AnnotationSaveDTO.frames}，服务端在一次请求内逐帧落库。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnnotationFrameDTO implements Serializable {

    /** 该帧的资产ID（必填；为空的帧会被跳过） */
    private Long activeAssetId;
    /** 该帧所属条目文件名（可选，随存档写入） */
    private String activeItem;
    /** 当前工具（可选） */
    private String tool;
    /**
     * 该帧的标注对象集合。
     *
     * <p>为空数组表示这一帧被删空了，仍需落库一次以清掉库里的旧对象，
     * 否则会出现「删完保存，重新进入又回来了」。
     */
    private List<AnnotationObjectDTO> objects;
    /**
     * 该帧的全局属性值（可选）：只在前端编辑过该帧全局属性时携带；
     * 未携带的帧由服务端保留其已存的 annotation_json $.globalAttrs（见 persistAssetAnnotation）。
     */
    private Map<String, Object> globalAttrs;
}
