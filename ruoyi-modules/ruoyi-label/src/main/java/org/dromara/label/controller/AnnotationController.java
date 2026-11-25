package org.dromara.label.controller;

import java.util.List;
import java.util.Map;
import org.dromara.label.common.LabelJobStatus;
import org.dromara.label.common.PageResult;
import org.dromara.label.common.Result;
import org.dromara.label.dto.AnnotationSaveDTO;
import org.dromara.label.dto.ObjectCommentDTO;
import org.dromara.label.service.AnnotationService;
import org.dromara.label.vo.AnnotationSaveResultVO;
import org.dromara.label.vo.AnnotationViewVO;
import org.dromara.label.vo.AssetAnnotationVO;
import org.dromara.label.vo.JobVO;
import org.dromara.label.vo.ObjectCommentVO;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 标注域（标注工作台、保存标注/草稿/提交、资产标注、我的作业）。 */
@RestController
public class AnnotationController {

  @Autowired
  private AnnotationService annotationService;

  @GetMapping("/jobs/{id}/annotation")
  public Result<AnnotationViewVO> jobAnnotation(@PathVariable Long id) {
    return Result.success(annotationService.annotation(id));
  }

  @PostMapping("/jobs/{id}/annotation")
  public Result<AnnotationSaveResultVO> createAnnotation(
      @PathVariable Long id, @RequestBody AnnotationSaveDTO dto) {
    return Result.success("保存成功", annotationService.saveAnnotation(id, dto, LabelJobStatus.LABELING));
  }

  @PutMapping("/jobs/{id}/annotation")
  public Result<AnnotationSaveResultVO> updateAnnotation(
      @PathVariable Long id, @RequestBody AnnotationSaveDTO dto) {
    return Result.success("保存成功", annotationService.saveAnnotation(id, dto, LabelJobStatus.LABELING));
  }

  @PostMapping("/jobs/{id}/save-draft")
  public Result<AnnotationSaveResultVO> saveDraft(
      @PathVariable Long id, @RequestBody AnnotationSaveDTO dto) {
    return Result.success("草稿已保存", annotationService.saveAnnotation(id, dto, LabelJobStatus.LABELING));
  }

  @Log(title = "标注提交审核", businessType = BusinessType.UPDATE, isSaveRequestData = false)
  @PostMapping("/jobs/{id}/submit")
  public Result<AnnotationSaveResultVO> submitAnnotation(
      @PathVariable Long id, @RequestBody AnnotationSaveDTO dto) {
    return Result.success("已提交审核", annotationService.saveAnnotation(id, dto, LabelJobStatus.PENDING_REVIEW));
  }

  /** 框级批注列表：assetId 为空时返回该作业全部批注。 */
  @GetMapping("/jobs/{id}/object-comments")
  public Result<List<ObjectCommentVO>> objectComments(
      @PathVariable Long id, @RequestParam(required = false) Long assetId) {
    return Result.success(annotationService.objectComments(id, assetId));
  }

  @PostMapping("/jobs/{id}/object-comments")
  public Result<Long> addObjectComment(@PathVariable Long id, @Valid @RequestBody ObjectCommentDTO dto) {
    return Result.success("批注已保存", annotationService.addObjectComment(id, dto));
  }

  @DeleteMapping("/object-comments/{id}")
  public Result<Void> deleteObjectComment(@PathVariable Long id) {
    annotationService.deleteObjectComment(id);
    return Result.success("批注已删除", null);
  }

  /**
   * 更新批注修订状态（标注环节「确认已修改」）。
   * 标注员不能删除批注，只能把 open 标记为 corrected；取消确认则回到 open。
   */
  @PutMapping("/object-comments/{id}/status")
  public Result<Void> updateObjectCommentStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
    annotationService.updateObjectCommentStatus(id, body == null ? null : body.get("status"));
    return Result.success("批注状态已更新", null);
  }

  @GetMapping("/assets/{id}/annotation")
  public Result<AssetAnnotationVO> assetAnnotation(@PathVariable Long id) {
    return Result.success(annotationService.assetAnnotation(id));
  }

  /** 帧级全局属性值：该资产最近一次存档的 annotation_json $.globalAttrs（无存档返回空对象）。 */
  @GetMapping("/assets/{id}/global-attrs")
  public Result<Map<String, Object>> assetGlobalAttrs(@PathVariable Long id) {
    return Result.success(annotationService.assetGlobalAttrs(id));
  }

  /** 标记 / 取消单帧无效（多帧条目中个别帧损坏/无关时使用）。 */
  @PostMapping("/assets/{id}/invalid")
  public Result<Void> markFrameInvalid(
      @PathVariable Long id, @RequestBody Map<String, Object> body) {
    boolean invalid = Boolean.TRUE.equals(body.get("invalid"));
    String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason"));
    annotationService.markFrameInvalid(id, invalid, reason);
    return Result.success(invalid ? "已标记为无效帧" : "已恢复为正常帧", null);
  }

  @GetMapping("/jobs/mine")
  public Result<PageResult<JobVO>> myJobs(
      @RequestParam(value = "status", required = false) String status) {
    return Result.success(annotationService.myJobs(status));
  }
}
