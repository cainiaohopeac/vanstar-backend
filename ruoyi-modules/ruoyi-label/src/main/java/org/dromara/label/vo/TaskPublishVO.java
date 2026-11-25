package org.dromara.label.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 任务发布结果 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskPublishVO implements Serializable {

    private Long taskId;
    private String status;
}
