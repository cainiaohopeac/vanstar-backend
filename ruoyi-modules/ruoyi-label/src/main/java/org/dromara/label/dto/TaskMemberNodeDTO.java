package org.dromara.label.dto;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import lombok.Data;

/**
 * 任务成员归属节点调整请求：把已是任务成员的某个用户归入指定工作流节点。
 */
@Data
public class TaskMemberNodeDTO implements Serializable {

    /** 要调整的任务成员用户ID */
    @NotNull(message = "请选择要调整的任务成员")
    private Long userId;

    /** 归属的工作流节点标识（label/review_N 等） */
    @NotNull(message = "请选择成员归属的工作流节点")
    private String nodeKey;
}
