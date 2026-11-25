package org.dromara.label.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 看板待办 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardTodoVO implements Serializable {

    private String type;
    private String title;
    private Long count;
    private String path;
}
