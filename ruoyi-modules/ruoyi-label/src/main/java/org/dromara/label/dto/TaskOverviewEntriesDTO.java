package org.dromara.label.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * 任务详情「总览」条目分页查询 DTO。
 *
 * <p>taskId / batchId 均为必填；statusList/keyword 可选。
 * 可见性由 service 按当前登录用户身份自动判定：超管 / 团队负责人 / 任务管理员
 * 看该 task+batch 下全部条目；普通成员仅看本人参与过（领取过）的条目。
 * 节点流转字段由 SQL LEFT JOIN 最新节点记录拼上（无记录时为空）；
 * statusList 仅在前端按页签显式传入时按最新节点记录状态过滤。</p>
 */
@Data
public class TaskOverviewEntriesDTO implements Serializable {

    @NotNull(message = "任务ID不能为空")
    private Long taskId;

    @NotNull(message = "batchId 不能为空")
    private Long batchId;

    /** 可选：节点状态筛选（pending/claimed/skipped/abandoned/done），空时不过滤（全部条目）。 */
    private List<String> statusList;

    /** 可选：模糊匹配条目名 / 条目号。 */
    private String keyword;

    @Min(value = 1, message = "pageNum 必须 ≥ 1")
    private Integer pageNum;

    @Min(value = 1, message = "pageSize 必须 ≥ 1")
    @Max(value = 100, message = "pageSize 必须 ≤ 100")
    private Integer pageSize;
}