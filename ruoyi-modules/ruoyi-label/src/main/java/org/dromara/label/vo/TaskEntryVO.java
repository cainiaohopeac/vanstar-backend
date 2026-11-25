package org.dromara.label.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务数据条目 VO（任务详情页左侧批次 + 右侧条目列表使用）。
 *
 * <p>一个条目 = 一个数据集合包（label_data_entry）：一次上传的数据默认整包为 1 个条目，
 * 也可按设置平均分割成多个条目；条目下挂载若干数据资产。标注状态与审核状态是两个
 * 相互独立的维度：审核通过不修改标注状态。对应界面列：条目号 / 所属上传（数据集合包）/
 * 数据量 / 导入批次 / 数据类型 / 标注状态 / 审核状态 / 标注团队 / 标注员 /
 * 当前审核人 / 不通过次数 / 变更时间。</p>
 *
 * <p>说明：abandonBy / skipBy 字段仅供业务判定（如"本人放弃/跳过的条目可恢复"）使用，
 * 不在条目列表中展示，因此不再附带 abandonByName/skipByName/abandonTime/skipTime。
 * 进度 / 数据来源 已从 VO 与 SQL 中移除，列表列不再展示。</p>
 */
@Data
public class TaskEntryVO implements Serializable {

    /** 条目ID（label_data_entry.id） */
    private Long id;
    /** 条目编号（系统生成，唯一） */
    private String entryNo;
    /** 条目名称（数据集合包名，默认取上传文件名） */
    private String entryName;
    /** 上传源文件（原始压缩包名，拆分条目时冗余便于直接展示） */
    private String sourceFile;
    /** 条目包含的数据条数 */
    private Long assetCount;
    /** 条目内无效帧数（label_asset.frame_flag = 'invalid' 的数据条数；条目级字段，不随工作流节点变化） */
    private Long invalidFrameCount;
    /** 条目号（兼容列，取条目名称） */
    private String fileName;
    /** 条目封面（包内首条数据） */
    private String fileUrl;
    private String thumbnailUrl;
    /** 所属批次 */
    private Long batchId;
    private String batchName;
    /** 数据类型：image / point_cloud */
    private String mediaType;
    /** 解析状态 */
    private String parseStatus;

    /**
     * 条目标注状态（字典 label_entry_status）：
     * pending/labeling/labeled/unrevised/revising/revised/skipped/abandoned
     */
    private String labelStatus;
    /** 条目审核状态（字典 label_review_status）：none/reviewing/rejected/re_reviewing/re_rejected/passed */
    private String reviewStatus;
    /** 审核轮次（领取一次审核 +1） */
    private Integer reviewRound;

    /** 标注员 */
    private Long assigneeId;
    private String assigneeName;
    /** 当前审核人 */
    private Long reviewerId;
    private String reviewerName;
    /** 不通过次数 */
    private Integer rejectCount;

    /** 放弃人ID（仅供业务判定"本人放弃的条目可恢复"，不在列表展示） */
    private Long abandonBy;
    /** 跳过人ID（仅供业务判定"本人跳过的条目可恢复"，不在列表展示） */
    private Long skipBy;

    /** 标注团队（任务下发团队，多团队逗号拼接） */
    private String teamName;

    private Long taskId;
    private String taskName;
    private Long projectId;
    private String projectName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime assignTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reviewTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    // ---------------- 当前节点（条目所处工作流环节，列表展示用） ----------------

    /** 条目当前所处的工作流节点（标注节点 label / 审核节点 key，本级已通过时为空） */
    private String currentNodeKey;
    /** 条目当前所处节点名称（本级审核已通过时显示「已通过」） */
    private String currentNodeName;

    // ---------------- 节点状态（任务详情页按工作流节点切换时填充） ----------------

    /** 当前查看的工作流节点 */
    private String nodeKey;
    /** 当前节点的状态：pending / claimed / skipped / abandoned / done */
    private String nodeStatus;
    /** 当前节点的处理人ID */
    private Long nodeOperatorId;
    /** 当前节点的处理人名称 */
    private String nodeOperatorName;
    /** 当前节点跳过/放弃前状态 */
    private String nodePrevStatus;
    /** 当前节点跳过人ID */
    private Long nodeSkipBy;
    private String nodeSkipByName;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime nodeSkipTime;
    /** 当前节点放弃人ID */
    private Long nodeAbandonBy;
    private String nodeAbandonByName;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime nodeAbandonTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime nodeClaimTime;
}
