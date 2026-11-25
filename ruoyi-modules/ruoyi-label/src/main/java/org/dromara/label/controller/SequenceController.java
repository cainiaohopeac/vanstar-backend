package org.dromara.label.controller;

import java.util.List;
import org.dromara.label.common.Result;
import org.dromara.label.dto.CommentAddDTO;
import org.dromara.label.dto.PrecheckRunDTO;
import org.dromara.label.dto.SequenceAnnotationSaveDTO;
import org.dromara.label.service.SequenceService;
import org.dromara.label.vo.AnnotationErrorVO;
import org.dromara.label.vo.CommentVO;
import org.dromara.label.vo.FrameVO;
import org.dromara.label.vo.IdVO;
import org.dromara.label.vo.PrecheckVO;
import org.dromara.label.vo.SavedCountVO;
import org.dromara.label.vo.SensorDataVO;
import org.dromara.label.vo.SensorVO;
import org.dromara.label.vo.SequenceAnnotationVO;
import org.dromara.label.vo.SequenceImportVO;
import org.dromara.label.vo.SequenceVO;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 多传感器多帧序列数据接口（阶段1）。 */
@RestController
public class SequenceController {

  @Autowired
  private SequenceService sequenceService;

  @GetMapping("/sequences")
  public Result<List<SequenceVO>> sequences(
      @RequestParam("projectId") Long projectId,
      @RequestParam(value = "batchId", required = false) Long batchId) {
    return Result.success(sequenceService.sequences(projectId, batchId));
  }

  @GetMapping("/sequences/{id}")
  public Result<SequenceVO> sequence(@PathVariable Long id) {
    return Result.success(sequenceService.sequence(id));
  }

  @GetMapping("/sequences/{id}/frames")
  public Result<List<FrameVO>> frames(@PathVariable Long id) {
    return Result.success(sequenceService.frames(id));
  }

  @GetMapping("/frames/{id}/sensor-data")
  public Result<List<SensorDataVO>> frameSensorData(@PathVariable Long id) {
    return Result.success(sequenceService.frameSensorData(id));
  }

  @GetMapping("/sensors")
  public Result<List<SensorVO>> sensors(
      @RequestParam(value = "projectId", required = false) Long projectId) {
    return Result.success(sequenceService.sensors(projectId));
  }

  @Log(title = "序列数据导入", businessType = BusinessType.IMPORT, isSaveRequestData = false)
  @PostMapping("/projects/{id}/sequences/import")
  public Result<SequenceImportVO> importSequence(
      @PathVariable Long id,
      @RequestParam(value = "batchId", required = false) Long batchId,
      @RequestParam("file") MultipartFile file) {
    return Result.success("序列导入完成", sequenceService.importNuscenesSequence(id, batchId, file));
  }

  @GetMapping("/sequences/{id}/annotation")
  public Result<SequenceAnnotationVO> annotationOfSequence(@PathVariable Long id) {
    return Result.success(sequenceService.annotationOfSequence(id));
  }

  @Log(title = "序列标注保存", businessType = BusinessType.UPDATE, isSaveRequestData = false)
  @PostMapping("/sequences/{id}/annotation")
  public Result<SavedCountVO> saveSequenceAnnotation(
      @PathVariable Long id, @RequestBody SequenceAnnotationSaveDTO dto) {
    return Result.success("保存成功", sequenceService.saveSequenceAnnotation(id, dto));
  }

  @GetMapping("/sequences/{id}/errors")
  public Result<List<AnnotationErrorVO>> annotationErrors(@PathVariable Long id) {
    return Result.success(sequenceService.annotationErrors(id));
  }

  @GetMapping("/sequences/{id}/prechecks")
  public Result<List<PrecheckVO>> prechecks(@PathVariable Long id) {
    return Result.success(sequenceService.prechecks(id));
  }

  @GetMapping("/sequences/{id}/comments")
  public Result<List<CommentVO>> comments(@PathVariable Long id) {
    return Result.success(sequenceService.comments(id));
  }

  @Log(title = "序列批注", businessType = BusinessType.INSERT)
  @PostMapping("/sequences/{id}/comments")
  public Result<IdVO> addComment(@PathVariable Long id, @RequestBody CommentAddDTO dto) {
    return Result.success("批注已添加", sequenceService.addComment(id, dto));
  }

  @Log(title = "序列预检执行", businessType = BusinessType.OTHER)
  @PostMapping("/sequences/{id}/precheck")
  public Result<List<PrecheckVO>> runPrecheck(
      @PathVariable Long id, @RequestBody PrecheckRunDTO dto) {
    return Result.success(sequenceService.runPrecheck(id, dto.getFrameId()));
  }

  @Log(title = "标注错误订正", businessType = BusinessType.UPDATE)
  @PutMapping("/errors/{id}/correct")
  public Result<Void> correctError(@PathVariable Long id) {
    sequenceService.correctError(id);
    return Result.success("已订正", null);
  }

  @Log(title = "批注订正", businessType = BusinessType.UPDATE)
  @PutMapping("/comments/{id}/correct")
  public Result<Void> correctComment(@PathVariable Long id) {
    sequenceService.correctComment(id);
    return Result.success("已订正", null);
  }
}
