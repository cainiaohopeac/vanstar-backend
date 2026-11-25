package org.dromara.label.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 添加序列批注请求
 */
@Data
public class CommentAddDTO implements Serializable {

    private Long frameId;
    /** 批注节点（review/error/...），缺省 review */
    private String node = "review";
    private String content;
}
