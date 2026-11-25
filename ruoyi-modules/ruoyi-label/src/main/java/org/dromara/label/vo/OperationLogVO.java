package org.dromara.label.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志 VO
 */
@Data
public class OperationLogVO implements Serializable {

    private Long id;
    private String action;
    private String operator;
    /** 目标对象（type#id） */
    private String target;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
