package org.dromara.label.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 项目标注文档（项目维度单 PDF，uk_project_id 保证项目下最多一份）。
 *
 * <p>用于项目详情页「标注文档」标签与标注页（image / pointCloud / sequence）工具栏
 * PDF 预览抽屉；项目创建者可上传/替换/删除，项目成员只读预览。</p>
 */
@Data
public class ProjectDoc implements Serializable {

    private Long id;
    /** 项目ID（唯一）。 */
    private Long projectId;
    /** 原始文件名（用于展示、下载）。 */
    private String fileName;
    /** MinIO 对象名（去前导 / 即可走 /uploads/** 网关）。 */
    private String objectName;
    /** MinIO bucket 名。 */
    private String bucketName;
    /** 文件大小（字节）。 */
    private Long fileSize;
    /** 内容类型（默认 application/pdf）。 */
    private String contentType;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
    private Long createdBy;
    private Long updatedBy;
    /** 软删除标记：0 正常 / 1 已删除。 */
    private Integer deleted;
}