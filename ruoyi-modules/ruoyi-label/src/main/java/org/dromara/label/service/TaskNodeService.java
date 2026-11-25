package org.dromara.label.service;

import java.util.List;
import org.dromara.label.vo.NodeClaimAutoVO;
import org.dromara.label.vo.TaskEntryVO;
import org.dromara.label.vo.TaskVO;

/**
 * 任务条目按工作流节点统一操作服务：池子/待办/跳过/放弃/恢复。
 *
 * <p>与原有 {@link TaskService#assignEntries} 等标注专用接口并行存在；
 * 节点下拉场景下，前端根据所选节点类型选择调用。</p>
 */
public interface TaskNodeService {

  /**
   * 在指定节点领取/分配条目。
   *
   * @param taskId 任务ID
   * @param entryIds 条目ID集合
   * @param nodeKey 节点标识
   * @param operatorId 目标处理人（null 表示领取给当前登录人）
   * @return 刷新后的任务视图
   */
  TaskVO claim(Long taskId, List<Long> entryIds, String nodeKey, Long operatorId);

  /**
   * 在指定节点自动领取一条池子内待处理条目（未勾选条目点「领取」时使用）。
   *
   * @param taskId 任务ID
   * @param nodeKey 节点标识
   * @return 领取结果（条目ID/名称/类型，供前端直接进入审核作业页）
   */
  NodeClaimAutoVO claimAuto(Long taskId, String nodeKey);

  /**
   * 定位当前登录人在指定节点下已领取（status='claimed'）的第一条待审核条目（按钮专用入口）。
   *
   * <p>与 {@link #claimAuto} 的区别：本方法不做任何领取动作，仅返回"我名下已领取"的 FIFO 第一条；
   * 按 n.claim_time ASC 升序排（先领的先审）。不影响当前 tab / scope 视图，
   * 避免「明明有我已领取的待办，但当前 tab 切到别处导致按钮点了没反应」的问题。</p>
   *
   * @param taskId 任务ID
   * @param nodeKey 节点标识
   * @return 我名下已领取的第一条（无则返回 id=null 的空 VO）
   */
  TaskEntryVO locateMyClaimed(Long taskId, String nodeKey);

  /**
   * 跳过指定节点下的条目（状态 pending / claimed → skipped）。
   */
  TaskVO skip(Long taskId, List<Long> entryIds, String nodeKey);

  /**
   * 放弃指定节点下的条目（状态 pending / claimed → abandoned）。
   */
  TaskVO abandon(Long taskId, List<Long> entryIds, String nodeKey);

  /**
   * 恢复指定节点下已跳过/已放弃的条目。
   */
  TaskVO restore(Long taskId, List<Long> entryIds, String nodeKey);
}
