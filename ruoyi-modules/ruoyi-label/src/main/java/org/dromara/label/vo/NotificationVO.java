package org.dromara.label.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 站内消息 VO
 */
@Data
public class NotificationVO implements Serializable {

    private Long id;
    private String title;
    private String content;
    private String bizType;
    private Long bizId;
    private Boolean read;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
