package org.dromara.label.dto;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import lombok.Data;

/**
 * 任务条目分页查询 DTO。
 *
 * <p>taskId 必填；batchId / tab / keyword / scope / nodeKey / pageNum / pageSize 均可选。
 * tab = pending(待分配/未领取) / claimed(我已领取) / skipped / abandoned / done；
 * scope = myteam(本团队) / mine(仅本人)；nodeKey 决定走 label 或 review_N 等节点过滤。
 * 可见性由 service 按当前登录用户身份自动判定。</p>
 */
@Data
public class TaskEntryListQueryDTO implements Serializable {

  @NotNull(message = "任务ID不能为空")
  private Long taskId;
  /** 批次ID（可选，为空时跨批次聚合） */
  private Long batchId;
  /** 页签 key（可选）：pending / claimed / skipped / abandoned / done */
  private String tab;
  /** 模糊匹配条目名 / 条目号 / 文件名（可选） */
  private String keyword;
  /** 范围：myteam / mine（可选） */
  private String scope;
  /** 节点 key：label / review_N（可选，默认 label） */
  private String nodeKey;
  private Integer pageNum;
  private Integer pageSize;
}