package org.dromara.label.dto;

import java.io.Serializable;
import java.util.List;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 文本标注保存入参（草稿与提交审核共用）。
 *
 * <p>沿用视觉标注的<strong>全量语义</strong>：{@code entities} / {@code sentiments}
 * 是当前画布上的完整结果，服务端据此重建（先软删该标注下旧数据，再整体写入）。
 * 增量语义需要前端上报增删改明细，容易出现「删除未同步」的脏数据，这里不采用。
 *
 * <p>文本条目一个 job 对应一个 asset，数据量远小于点云（一篇文本通常几十到几百个实体），
 * 全量重建的代价可接受。
 */
@Data
public class TextAnnotationSaveDTO implements Serializable {

  /** 作业ID */
  @NotNull(message = "jobId 不能为空")
  private Long jobId;

  /** 数据资产ID（一篇文本） */
  @NotNull(message = "assetId 不能为空")
  private Long assetId;

  /** 实体标注列表（全量；可为空表示本次清空所有实体） */
  private List<TextEntitySaveDTO> entities;

  /** 情感分类列表（条目级与句级混在一起，按 levelType 区分） */
  private List<TextSentimentSaveDTO> sentiments;

  /** 整篇批注（标注员备注） */
  private String comment;

  /**
   * 保存意图：draft 草稿 / pending_review 提交审核。
   * 与视觉标注的 status 语义保持一致，便于复用作业状态推进逻辑。
   */
  private String status;
}
