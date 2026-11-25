package org.dromara.label.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目标注文档 VO（含上传人姓名与可直接预览的 URL）。
 */
@Data
public class ProjectDocVO {

    private Long id;
    private Long projectId;
    private String fileName;
    private Long fileSize;
    private String contentType;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
    /** 上传人 ID。 */
    private Long uploadedBy;
    /** 上传人姓名（取自 sys_user.nick_name）。 */
    private String uploadedByName;

    /**
     * MinIO 对象名（不返回给前端；前端通过 /uploads/{objectName} 拼接 URL 直接预览）。
     */
    private transient String objectName;
    private transient String bucketName;
}