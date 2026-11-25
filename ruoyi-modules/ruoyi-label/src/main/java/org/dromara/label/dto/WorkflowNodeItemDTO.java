package org.dromara.label.dto;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * 工作流节点配置项：项目「工作流配置」标签页保存时提交的单个节点。
 *
 * <p>固定节点（原始数据/标注/合格数据）由后端按 nodeType 识别，只允许改名与排序；
 * 审核节点支持新增（nodeKey 为空时后端自动生成 review_N）。</p>
 */
@Data
public class WorkflowNodeItemDTO implements Serializable {

  /** 节点标识：已存在节点传原 key；新增审核节点可留空，由后端生成 */
  private String nodeKey;

  /** 节点名称 */
  private String nodeName;

  /** 节点类型：start/label/review/end */
  private String nodeType;

  /** 流转顺序（升序） */
  private Integer sortOrder;

  /** 节点说明 */
  private String remark;

  /**
   * 审核节点的批注类型（错误内容）：漏点 / 漏标 / 不贴合 / 混点 / 类别错误，可自定义追加。
   * 仅审核节点有效，为空时后端按默认类型补齐；非审核节点忽略。
   */
  private List<String> commentTypes;
}
