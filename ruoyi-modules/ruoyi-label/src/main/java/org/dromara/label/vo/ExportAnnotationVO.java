package org.dromara.label.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;

/**
 * 导出数据集中标注对象条目 VO
 */
@Data
public class ExportAnnotationVO implements Serializable {

    private Long jobId;
    private String jobNo;
    private String mediaType;
    private Long assetId;
    private String fileName;
    private Long annotationId;
    private Integer version;
    private String annotationStatus;
    private String objectKey;
    private String objectType;
    private Long classId;
    private String className;
    private String color;
    /** 几何（解析后的对象） */
    private Object geometry;
    /** 属性（解析后的对象） */
    private Object attributes;
    /** SQL 暂存列，不对外输出 */
    @JsonIgnore
    private String geometryJson;
    /** SQL 暂存列，不对外输出 */
    @JsonIgnore
    private String attrJson;
}
