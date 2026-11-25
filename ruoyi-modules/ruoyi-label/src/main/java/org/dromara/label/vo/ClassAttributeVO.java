package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 标签属性 VO
 *
 * <p>随 {@link ClassVO} 一起返回：「编辑标签」弹窗据此回显已配置的属性（含 id），
 * 前端改完属性整份提交时再用 id 做新增 / 修改 / 删除的比对。</p>
 */
@Data
public class ClassAttributeVO implements Serializable {

    /** 属性ID（前端据此判断是新增还是已有属性） */
    private Long id;

    /** 所属标签ID */
    private Long classId;

    /** 属性名称 */
    private String attrName;

    /** 属性类型：select=下拉选项 / text=文本输入 */
    private String attrType;

    /** 属性选项（select 类型生效；文本类型为空数组） */
    private List<Object> attrOptions;

    /** DB 读取中间态：attr_options 的 JSON 文本，由 Service 层 parseJson 后写入 attrOptions */
    private String attrOptionsJson;

    /** 是否必填：1=是 0=否 */
    private Integer required;

    /** 文本最大字符数（仅 attrType=text 生效，null 表示不限制、默认上限 200） */
    private Integer maxLength;
}
