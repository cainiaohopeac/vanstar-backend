package org.dromara.label.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * nuScenes 元数据包实体：从归档压缩包解析出的元数据表集合，
 * 作为服务间（导入 -&gt; 预标注 / 导出）传递的结构化载体。
 *
 * <p>tables 的 value 为 nuScenes JSON 行集合（List&lt;行&gt;），行内字段由
 * nuScenes 官方 schema 决定（token / sample_token / translation 等），
 * 属于 JSON 数据本身的动态形态，仅在服务实现内部流转。</p>
 */
@Data
public class NuscenesMetaBundle implements Serializable {

    /** nuScenes 版本目录名（v1.0-mini 等） */
    private String versionDir;
    /** 元数据来源压缩包名（审计展示用） */
    private String sourceFile;
    /** 元数据表集合：表名（sample_data / sample_annotation / ...） -&gt; 行列表 */
    private Map<String, List<Map<String, Object>>> tables;
}
