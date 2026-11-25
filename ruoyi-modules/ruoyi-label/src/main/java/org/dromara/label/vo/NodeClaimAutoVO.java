package org.dromara.label.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 审核节点自动领取结果 VO（任务详情页未勾选条目点「领取」时使用）：
 * 领取该节点池子中第一条待审核数据（已置为「审核中」、节点状态 claimed），
 * 前端据此直接跳转标注工作台的审核模式。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NodeClaimAutoVO implements Serializable {

    /** 条目ID（label_data_entry.id） */
    private Long entryId;
    /** 条目名称 */
    private String entryName;
    /** 数据类型：image / point_cloud / mixed（决定跳转哪个标注工作台） */
    private String mediaType;
}
