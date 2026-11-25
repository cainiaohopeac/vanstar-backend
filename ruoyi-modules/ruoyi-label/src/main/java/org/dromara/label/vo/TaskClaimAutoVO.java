package org.dromara.label.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 自动领取结果 VO（「领取标注」未勾选条目时使用）：
 * 被驳回的「未修订」待办最优先（source=revise，领取即置为修订中），
 * 其次是进行中的待办（source=todo），无待办则在任务池内随机领取一条
 * 待分配条目（source=random）；前端据此直接跳转对应标注工作台。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskClaimAutoVO implements Serializable {

    /** 条目ID（label_data_entry.id） */
    private Long entryId;
    /** 条目名称 */
    private String entryName;
    /** 数据类型：image / point_cloud / mixed（决定跳转哪个标注工作台） */
    private String mediaType;
    /** 条目归属的作业ID（可能为空：为空时前端提示稍后在「我的标注作业」查看） */
    private Long jobId;
    /** 领取来源：revise=进入被驳回的待修订条目（已置为修订中）；todo=进入本人已有待办；random=随机分配了一条待分配条目 */
    private String source;
}
