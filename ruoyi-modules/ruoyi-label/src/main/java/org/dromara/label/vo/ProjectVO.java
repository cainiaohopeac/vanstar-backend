package org.dromara.label.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 项目 VO
 */
@Data
public class ProjectVO implements Serializable {

    private Long id;
    /** 项目编码 */
    private String projectCode;
    /** 项目名称 */
    private String projectName;
    /** 描述 */
    private String description;
    /** 数据类型 */
    private String dataType;
    /** 状态 */
    private String status;
    /** 负责人ID */
    private Long ownerId;
    /** 主队伍ID */
    private Long teamId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    /** 负责人名称 */
    private String ownerName;
    /** 主队伍名称 */
    private String teamName;
    /** 项目创建者用户 ID（用于前端判断当前用户是否为创建者，控制标注文档上传/删除权限） */
    private Long createdBy;
    /** 资产总数 */
    private Long assetCount;
    /** 标注进度 0~100 */
    private BigDecimal progress;
    /** 参与队伍ID列表 */
    private List<Long> teamIds;
    /** 参与队伍名称列表 */
    private List<String> teamNames;
}
