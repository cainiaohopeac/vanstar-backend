package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 可导出批次 VO（创建导出时批次下拉）
 * <p>「已完成批次」：批次内全部数据条目均通过全部审核节点（合格数据）。</p>
 */
@Data
public class ExportBatchVO implements Serializable {

    private Long id;
    /** 批次号（系统生成，唯一） */
    private String batchNo;
    /** 批次名称（缺省回退批次号） */
    private String batchName;
    /** 批次内数据条目数 */
    private Long entryCount;
    /** 批次内合格数据资产数（即可导出资产数） */
    private Long assetCount;
}
