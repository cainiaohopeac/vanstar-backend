package org.dromara.label.service;

import java.util.List;
import org.dromara.label.common.PageResult;
import org.dromara.label.dto.EntryReviewDTO;
import org.dromara.label.dto.ReviewRejectDTO;
import org.dromara.label.vo.JobVO;
import org.dromara.label.vo.ReviewEntryVO;
import org.dromara.label.vo.ReviewRecordVO;

/**
 * 审核域服务：作业级审核/验收（兼容存量）、条目级审核（领取/通过/驳回、可多轮复审）、审核记录查询。
 */
public interface ReviewService {

  /** 审核作业列表：仅包含审核/验收相关状态的作业。 */
  PageResult<JobVO> reviews(String status);

  /** 审核通过：作业进入待验收。 */
  JobVO approveReview(Long jobId);

  /** 审核退回：作业退回标注并记录退回意见。 */
  JobVO rejectReview(Long jobId, ReviewRejectDTO dto);

  /** 验收通过：作业完成入库并累计任务完成数。 */
  JobVO acceptReview(Long jobId);

  /** 作业审核记录。 */
  List<ReviewRecordVO> reviewRecords(Long jobId);

  // ---------------- 条目级审核 ----------------

  /**
   * 条目级审核列表（跨任务）。
   *
   * @param taskId   任务过滤（可空）
   * @param status   页签：pending 待领取 / reviewing 审核中 / re_reviewing 复审中 /
   *                 passed 已通过 / rejected 已驳回 / mine 我领取的（可空=全部）
   * @param keyword  关键字（任务编号/名称、条目文件、批次名）
   */
  PageResult<ReviewEntryVO> reviewEntries(Long taskId, String status, String keyword,
      int pageNum, int pageSize);

  /** 领取审核：把选中条目领取为当前审核人（已标注→审核中，已修订→复审中），返回领取数。 */
  int claimEntries(EntryReviewDTO dto);

  /** 审核通过：不修改标注状态，仅把审核状态置为已通过，返回通过数。 */
  int approveEntries(EntryReviewDTO dto);

  /** 审核驳回：标注状态回到未修订，审核状态置为审核不通过/复审不通过，返回驳回数。 */
  int rejectEntries(EntryReviewDTO dto);

  /** 单个条目的审核历史。 */
  List<ReviewRecordVO> entryReviewRecords(Long assetId);
}
