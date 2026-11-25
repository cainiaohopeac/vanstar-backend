package org.dromara.label.controller;

import java.util.List;
import jakarta.validation.Valid;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.label.common.Result;
import org.dromara.label.dto.TextAnnotationSaveDTO;
import org.dromara.label.service.TextAnnotationService;
import org.dromara.label.vo.TextAnnotationSaveResultVO;
import org.dromara.label.vo.TextAnnotationVO;
import org.dromara.label.vo.TextAssetVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文本标注工作台接口。
 *
 * <p>标识一律作为参数传递，不放请求路径（路径上的 id 会被网关/日志原样记录，
 * 且不便于统一做参数校验）；参数多于两个时统一走 POST + 实体。
 */
@RestController
@RequestMapping("/label/text/annotation")
public class TextAnnotationController {

  private final TextAnnotationService textAnnotationService;

  public TextAnnotationController(TextAnnotationService textAnnotationService) {
    this.textAnnotationService = textAnnotationService;
  }

  /** 加载标注工作台数据（正文、句边界、已有标注、标签树）。 */
  @GetMapping("/view")
  public Result<TextAnnotationVO> view(@RequestParam Long jobId, @RequestParam Long assetId) {
    return Result.success(textAnnotationService.view(jobId, assetId));
  }

  /** 作业内的文本条目列表。 */
  @GetMapping("/entries")
  public Result<List<TextAssetVO>> jobAssets(@RequestParam Long jobId) {
    return Result.success(textAnnotationService.jobAssets(jobId));
  }

  /** 保存标注（dto.status = pending_review 时为提交审核，其余为草稿）。 */
  @Log(title = "文本标注保存", businessType = BusinessType.UPDATE, isSaveRequestData = false)
  @PostMapping("/save")
  public Result<TextAnnotationSaveResultVO> save(@Valid @RequestBody TextAnnotationSaveDTO dto) {
    return Result.success("保存成功", textAnnotationService.save(dto));
  }
}
