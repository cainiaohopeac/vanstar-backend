package org.dromara.label.dto;

import java.io.Serializable;
import lombok.Data;

/**
 * 任务列表分页查询 DTO（POST /tasks/list 请求体）。
 *
 * <p>所有条件均可选；scope 决定可见范围：manage=任务管理（仅自己创建）、
 * received=数据任务列表（本人负责团队 + 本人被配置为任务成员）、空=兼容旧调用
 * （团队负责人限定本团队，其他角色可见全部）。</p>
 */
@Data
public class TaskQueryDTO implements Serializable {

    /** 可选：任务号模糊查询。 */
    private String taskNo;

    /** 可选：任务名称模糊查询。 */
    private String taskName;

    /** 可选：项目名称模糊查询。 */
    private String projectName;

    /** 可选：任务状态过滤（字典 label_task_status）。 */
    private String status;

    /** 可选：团队名称过滤（精确匹配）。 */
    private String teamName;

    /** 可选：可见范围（manage / received；空时兼容旧调用口径）。 */
    private String scope;
}
