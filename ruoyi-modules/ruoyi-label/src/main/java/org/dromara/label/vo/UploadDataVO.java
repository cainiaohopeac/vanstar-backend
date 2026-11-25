package org.dromara.label.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 上传数据 VO。
 *
 * <p>一个数据批次可分多次上传（zip 数据包或单文件），每次上传对应一条上传数据，
 * 上传数据之间彼此独立，可单独查看其下数据、单独删除，互不影响。</p>
 */
@Data
public class UploadDataVO implements Serializable {

    private Long id;
    /** 所属项目ID */
    private Long projectId;
    /** 所属批次ID */
    private Long batchId;
    /** 上传编号（系统生成，唯一） */
    private String uploadNo;
    /** 上传文件名 */
    private String fileName;
    /** 上传文件大小（字节） */
    private Long fileSize;
    /** MinIO 原始对象名（大文件导入场景） */
    private String objectName;
    /** 来源类型：zip/single/legacy */
    private String sourceType;
    /** 该上传数据下的数据条数（实时统计） */
    private Long assetCount;
    /** 该上传数据切分出的数据条目数（数据集合包，实时统计） */
    private Long entryCount;
    /** 处理状态 */
    private String status;
    /** 备注 */
    private String remark;
    /** 上传人 */
    private String createByName;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
