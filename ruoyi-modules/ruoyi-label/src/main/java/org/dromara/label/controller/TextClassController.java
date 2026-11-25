package org.dromara.label.controller;

import java.util.List;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.label.common.Result;
import org.dromara.label.entity.LabelTextClass;
import org.dromara.label.entity.LabelTextClassSet;
import org.dromara.label.service.TextClassService;
import org.dromara.label.vo.TextClassVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文本标签体系管理接口（与视觉标签互相独立）。
 */
@RestController
@RequestMapping("/label/text/class")
public class TextClassController {

  private final TextClassService textClassService;

  public TextClassController(TextClassService textClassService) {
    this.textClassService = textClassService;
  }

  /** 标签树（含实体、关系、情感三类节点）。 */
  @GetMapping("/tree")
  public Result<List<TextClassVO>> tree(@RequestParam Long projectId) {
    return Result.success(textClassService.tree(projectId));
  }

  /** 项目下的标签集列表。 */
  @GetMapping("/sets")
  public Result<List<LabelTextClassSet>> classSets(@RequestParam Long projectId) {
    return Result.success(textClassService.classSets(projectId));
  }

  /** 新建或更新标签集（有 id 为更新）。 */
  @Log(title = "文本标签集保存", businessType = BusinessType.UPDATE)
  @PostMapping("/set/save")
  public Result<Long> saveClassSet(@RequestBody LabelTextClassSet row) {
    return Result.success("保存成功", textClassService.saveClassSet(row));
  }

  /** 新建或更新标签（有 id 为更新）。 */
  @Log(title = "文本标签保存", businessType = BusinessType.UPDATE)
  @PostMapping("/save")
  public Result<Long> saveClass(@RequestBody LabelTextClass row) {
    return Result.success("保存成功", textClassService.saveClass(row));
  }

  /**
   * 删除标签。已被标注使用或仍有子标签时会拒绝。
   *
   * <p>单个 id 也走 POST + 请求参数而非路径，与整体约定保持一致。
   */
  @Log(title = "文本标签删除", businessType = BusinessType.DELETE)
  @PostMapping("/delete")
  public Result<String> deleteClass(@RequestParam Long classId) {
    textClassService.deleteClass(classId);
    return Result.success("删除成功");
  }
}
