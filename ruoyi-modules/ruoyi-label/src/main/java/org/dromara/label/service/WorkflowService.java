package org.dromara.label.service;

import java.util.List;
import org.dromara.label.common.LabelJobStatus;
import org.dromara.label.dto.WorkflowNodeSaveDTO;
import org.dromara.label.vo.WorkflowNodeVO;
import org.dromara.label.vo.WorkflowTaskProgressVO;

/**
 * 项目工作流域服务：项目级动态工作流节点配置（原始数据 → 标注 → 动态审核节点 → 合格数据）
 * 与任务节点流转。
 *
 * <p>项目之间完全隔离：每个项目一套节点配置，任务流转只受所属项目的节点影响。</p>
 *
 * <p>固定节点 raw_data(start) / label(label) / qualified(end) 不可删除；
 * 审核节点（review）默认一个，可增删、改名、排序。暂不设验收节点：
 * 最后一个审核节点全部通过即进入「合格数据」。</p>
 */
public interface WorkflowService {

  /** 节点类型：原始数据（固定起点） */
  String NODE_TYPE_START = "start";
  /** 节点类型：标注（固定） */
  String NODE_TYPE_LABEL = "label";
  /** 节点类型：审核（动态，可增删） */
  String NODE_TYPE_REVIEW = "review";
  /** 节点类型：合格数据（固定终点） */
  String NODE_TYPE_END = "end";

  /** 固定节点标识：原始数据 */
  String KEY_RAW_DATA = "raw_data";
  /** 固定节点标识：标注（任务成员默认归属节点） */
  String KEY_LABEL = "label";
  /** 固定节点标识：合格数据 */
  String KEY_QUALIFIED = "qualified";

  /** 节点状态：已完成 */
  String STATE_DONE = "done";
  /** 节点状态：进行中（当前节点） */
  String STATE_CURRENT = "current";
  /** 节点状态：未开始 */
  String STATE_TODO = "todo";

  /** 任务状态：已退回（审核驳回，需回到标注节点修订） */
  String TASK_STATUS_REJECTED = LabelJobStatus.REVIEW_REJECTED;

  /** 审核节点默认批注类型（错误内容）：画批注时的「错误内容」下拉默认项 */
  List<String> DEFAULT_COMMENT_TYPES = List.of("漏点", "漏标", "不贴合", "混点", "类别错误");

  /**
   * 项目工作流节点列表（按流转顺序）。
   * 项目尚未配置时自动初始化默认流程（原始数据 → 标注 → 审核 → 合格数据）。
   */
  List<WorkflowNodeVO> nodes(Long projectId);

  /** 保存项目工作流配置（整体覆盖：按提交顺序重排，未提交的节点软删除）。 */
  List<WorkflowNodeVO> saveNodes(WorkflowNodeSaveDTO dto);

  /** 初始化项目默认工作流（幂等：已配置的项目不做任何处理）。 */
  void initDefault(Long projectId);

  /** 任务工作流进度（当前节点 + 各节点状态），供任务详情「工作流」按钮展示。 */
  WorkflowTaskProgressVO taskProgress(Long taskId);

  /**
   * 按任务下条目的标注/审核进度同步任务当前节点（任务流转的唯一入口）。
   * 供标注提交、审核通过/驳回后回调，保证节点与条目状态一致。
   */
  void syncTaskNode(Long taskId);

  /** 任务当前有效节点：已退回（review_rejected）时视为回到标注节点修订。 */
  String effectiveNodeKey(Long taskId);

  /** 任务所属项目ID（任务不存在或已删除时返回 null）。 */
  Long projectIdOfTask(Long taskId);

  /**
   * 查询某个节点上生效的批注类型（错误内容下拉项）。
   *
   * <p>取数规则：指定节点是审核节点且已配置类型 → 用该节点的类型；
   * 否则退到项目下第一个配置过类型的审核节点；都没有配置时返回默认五类。</p>
   *
   * @param projectId 项目ID
   * @param nodeKey   节点标识（可为空，为空时直接按「第一个审核节点」取）
   */
  List<String> commentTypesOfNode(Long projectId, String nodeKey);
}
