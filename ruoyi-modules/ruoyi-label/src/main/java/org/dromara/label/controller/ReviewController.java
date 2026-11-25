package org.dromara.label.controller;

import java.util.List;
import org.dromara.label.common.PageResult;
import org.dromara.label.common.Result;
import org.dromara.label.dto.EntryReviewDTO;
import org.dromara.label.dto.ReviewRejectDTO;
import org.dromara.label.service.ReviewService;
import org.dromara.label.service.TaskService;
import org.dromara.label.vo.JobVO;
import org.dromara.label.vo.ReviewEntryVO;
import org.dromara.label.vo.ReviewRecordVO;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 审核域（作业级审核/验收、条目级审核领取/通过/驳回、审核记录）。 */
@RestController
public class ReviewController {

  @Autowired
  private ReviewService reviewService;

  @Autowired
  private TaskService taskService;

  @GetMapping("/reviews/jobs")
  public Result<PageResult<JobVO>> reviewJobs(
      @RequestParam(value = "status", required = false) String status) {
    return Result.success(reviewService.reviews(status));
  }

  @GetMapping("/reviews/jobs/{id}")
  public Result<JobVO> reviewJob(@PathVariable Long id) {
    return Result.success(taskService.job(id));
  }

  @Log(title = "作业审核通过", businessType = BusinessType.UPDATE)
  @PostMapping("/reviews/jobs/{id}/approve")
  public Result<JobVO> approveReview(@PathVariable Long id) {
    return Result.success("审核通过", reviewService.approveReview(id));
  }

  @Log(title = "作业审核驳回", businessType = BusinessType.UPDATE)
  @PostMapping("/reviews/jobs/{id}/reject")
  public Result<JobVO> rejectReview(@PathVariable Long id, @RequestBody ReviewRejectDTO dto) {
    return Result.success("已退回", reviewService.rejectReview(id, dto));
  }

  @Log(title = "作业验收通过", businessType = BusinessType.UPDATE)
  @PostMapping("/reviews/jobs/{id}/accept")
  public Result<JobVO> acceptReview(@PathVariable Long id) {
    return Result.success("验收通过", reviewService.acceptReview(id));
  }

  @GetMapping("/reviews/jobs/{id}/records")
  public Result<List<ReviewRecordVO>> reviewRecords(@PathVariable Long id) {
    return Result.success(reviewService.reviewRecords(id));
  }

  // ---------------- 条目级审核 ----------------

  /**
   * 条目级审核列表：status 页签 pending 待领取/reviewing 审核中/re_reviewing 复审中/
   * passed 已通过/rejected 已驳回/mine 我领取的。
   */
  @GetMapping("/reviews/entries")
  public Result<PageResult<ReviewEntryVO>> reviewEntries(
      @RequestParam(value = "taskId", required = false) Long taskId,
      @RequestParam(value = "status", required = false) String status,
      @RequestParam(value = "keyword", required = false) String keyword,
      @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
      @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
    return Result.success(reviewService.reviewEntries(taskId, status, keyword, pageNum, pageSize));
  }

  /** 领取审核：把选中条目领取为当前审核人（审核中/复审中）。 */
  @Log(title = "条目审核领取", businessType = BusinessType.UPDATE)
  @PostMapping("/reviews/entries/claim")
  public Result<Integer> claimEntries(@RequestBody EntryReviewDTO dto) {
    return Result.success("领取成功", reviewService.claimEntries(dto));
  }

  /** 审核通过：不修改标注状态，仅审核状态置为已通过。 */
  @Log(title = "条目审核通过", businessType = BusinessType.UPDATE, isSaveRequestData = false)
  @PostMapping("/reviews/entries/approve")
  public Result<Integer> approveEntries(@RequestBody EntryReviewDTO dto) {
    return Result.success("审核通过", reviewService.approveEntries(dto));
  }

  /** 审核驳回：标注状态回到未修订，等待领取修订。 */
  @Log(title = "条目审核驳回", businessType = BusinessType.UPDATE, isSaveRequestData = false)
  @PostMapping("/reviews/entries/reject")
  public Result<Integer> rejectEntries(@RequestBody EntryReviewDTO dto) {
    return Result.success("已驳回", reviewService.rejectEntries(dto));
  }

  /** 单个条目的审核历史。 */
  @GetMapping("/reviews/entries/{assetId}/records")
  public Result<List<ReviewRecordVO>> entryReviewRecords(@PathVariable Long assetId) {
    return Result.success(reviewService.entryReviewRecords(assetId));
  }
}
