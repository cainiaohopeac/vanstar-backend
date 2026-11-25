package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 条目 → 作业映射 VO（任务详情「查看」多选/全选时按作业分组打开只读工作台）
 */
@Data
public class EntryJobVO implements Serializable {

    /** 数据条目ID（label_data_entry.id） */
    private Long entryId;

    /** 条目归属的标注作业ID；条目尚未领取（无作业）时为 null */
    private Long jobId;
}
