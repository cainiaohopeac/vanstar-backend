package org.dromara.label.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 序列批注 VO
 */
@Data
public class CommentVO implements Serializable {

    private Long id;
    private Long sequenceId;
    private Long frameId;
    private String node;
    private String content;
    private String status;
    private Long creatorId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
