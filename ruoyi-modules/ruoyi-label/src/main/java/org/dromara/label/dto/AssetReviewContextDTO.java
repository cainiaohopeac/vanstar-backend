package org.dromara.label.dto;

import lombok.Data;

/**
 * 数据资产审核上下文（Mapper 返回专用 DTO）。
 *
 * <p>承载审核员修订模式（{@code editMode=reviewer}）需要的审核身份与状态：
 * <ul>
 *   <li>assetId      —— 关联 label_asset.id</li>
 *   <li>reviewStatus —— 当前审核状态（reviewing / re_reviewing / passed / rejected / none 等）</li>
 *   <li>reviewerId   —— 当前审核人 ID（未领取时为 null）</li>
 *   <li>entryId      —— 数据资产所属数据条目（数据集合包），未归入条目时为 null</li>
 * </ul>
 * </p>
 *
 * <p>仅按 {@code label_asset.id} 主键查询，无额外索引开销；
 * 资产不存在或已删除时 mapper 返回 null，调用方按 null 判定即可。</p>
 *
 * <p>非数据库对应实体（不实现 Serializable），仅作为 mapper 与 service 间的轻量数据传输载体。</p>
 */
@Data
public class AssetReviewContextDTO {

    /** 关联 label_asset.id */
    private Long assetId;
    /** 当前审核状态 */
    private String reviewStatus;
    /** 当前审核人 ID，未领取时为 null */
    private Long reviewerId;
    /** 所属数据条目 ID，未归入条目时为 null */
    private Long entryId;
}