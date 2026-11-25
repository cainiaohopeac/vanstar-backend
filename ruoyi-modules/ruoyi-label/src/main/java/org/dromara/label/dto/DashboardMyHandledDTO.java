package org.dromara.label.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * 首页「总览」- 成员在某任务某批次领取的条目查询请求 DTO。
 *
 * <p>任务 taskId + batchId 必填；返回该任务该批次下本人当前持有且未被释放的所有
 * 节点条目（不限节点：标注 / 审核 / 验收 都聚合；状态 claimed/skipped/abandoned/done
 * 四种，被释放后 operator_id 置 NULL 天然不命中）。</p>
 */
@Data
public class DashboardMyHandledDTO implements Serializable {

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