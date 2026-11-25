package org.dromara.label.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 任务拆分结果 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskSplitVO implements Serializable {

    private Long taskId;
    private long jobCount;
}
