package org.dromara.label.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任务详情「总览」条目 VO（跨节点聚合视图）。
 *
 * <p>继承 TaskEntryVO 复用条目级字段，并新增节点级字段：
 * <ul>
 *   <li>{@code entryNodeId}：label_entry_node 主键，用于跳转操作；</li>
 *   <li>{@code nodeType}：节点类型（label / review_N / acceptance 等）；</li>
 *   <li>{@code nodeDoneTime}：节点完成时间（status=done 时填充）。</li>
 * </ul>
 * 与按节点过滤的 {@link TaskEntryVO} 不同：每行即为一条节点级记录（不限节点类型）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TaskOverviewEntryVO extends TaskEntryVO {

    /** 节点记录ID（label_entry_node.id），用于跳转节点操作。 */
    private Long entryNodeId;

    /** 节点类型：label / review_N / acceptance 等。 */
    private String nodeType;

    /** 节点完成时间（status=done 时填充）。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime nodeDoneTime;
}