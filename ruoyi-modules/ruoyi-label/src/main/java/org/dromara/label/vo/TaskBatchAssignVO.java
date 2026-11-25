package org.dromara.label.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 任务批次分配结果 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskBatchAssignVO implements Serializable {

    private Long taskId;
    private Long batchId;
}
