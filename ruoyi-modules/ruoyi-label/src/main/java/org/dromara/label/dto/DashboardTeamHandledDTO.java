package org.dromara.label.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * 首页「总览」- 团队在某任务某批次领取的条目查询请求 DTO。
 *
 * <p>仅团队负责人 / 任务管理员 / 超管可访问；返回该任务该批次下所有已分配条目（不限 operator，
 * operator_id IS NOT NULL，节点不限），覆盖 claimed/skipped/abandoned/done。</p>
 */
@Data
public class DashboardTeamHandledDTO implements Serializable {

    @NotNull(message = "taskId 不能为空")
    private Long taskId;

    @NotNull(message = "batchId 不能为空")
    private Long batchId;

    /** 可选：节点状态筛选，支持多选；空时由 service 填充为默认 4 种。 */
    private List<String> statusList;

    /** 可选：模糊匹配条目名 / 条目号 / 任务名 / 项目名。 */
    private String keyword;

    @Min(value = 1, message = "pageNum 必须 ≥ 1")
    private Integer pageNum;

    @Min(value = 1, message = "pageSize 必须 ≥ 1")
    @Max(value = 100, message = "pageSize 必须 ≤ 100")
    private Integer pageSize;
}