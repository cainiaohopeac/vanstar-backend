package org.dromara.label.controller;

import java.util.List;

import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.label.common.Result;
import org.dromara.label.dto.LibraryImportDTO;
import org.dromara.label.dto.LibraryLabelDTO;
import org.dromara.label.dto.LibraryProjectComposeDTO;
import org.dromara.label.dto.LibraryQueryDTO;
import org.dromara.label.dto.LibrarySetDTO;
import org.dromara.label.service.LibraryService;
import org.dromara.label.vo.ClassSetVO;
import org.dromara.label.vo.LibraryLabelVO;
import org.dromara.label.vo.LibrarySetVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 标签库域：平台级标签集/标签的维护与项目引入。
 *
 * <p>接口约定（本控制器全部遵循）：<b>POST + JSON body</b>，请求路径不携带任何参数 ——
 * 入参两个以上的查询/操作一律走 body，删除类操作同样 POST 传 id（不做 DELETE 路径传参）。
 * 路径段语义：/label/library/set|label/* 维护库本身；
 * /label/library/project/* 把库标签复制进项目标签体系。</p>
 */
@RestController
@RequestMapping("/label/library")
public class LibraryController {

  @Autowired
  private LibraryService libraryService;

  // ---------------- 标签库维护 ----------------

  /** 标签集列表（body: scene 过滤，空=全部；含集内标签数）。 */
  @PostMapping("/set/list")
  public Result<List<LibrarySetVO>> sets(@RequestBody LibraryQueryDTO dto) {
    return Result.success(libraryService.sets(dto));
  }

  /** 新建/更新标签集（body 带 id 为更新）。 */
  @Log(title = "标签库集保存", businessType = BusinessType.UPDATE)
  @PostMapping("/set/save")
  public Result<LibrarySetVO> saveSet(@RequestBody LibrarySetDTO dto) {
    return Result.success("保存成功", libraryService.saveSet(dto));
  }

  /** 删除标签集（body: id；软删，连带集内标签）。 */
  @Log(title = "标签库集删除", businessType = BusinessType.DELETE)
  @PostMapping("/set/delete")
  public Result<Void> deleteSet(@RequestBody LibraryQueryDTO dto) {
    libraryService.deleteSet(dto);
    return Result.success("删除成功", null);
  }

  /** 标签列表（body: setId）。 */
  @PostMapping("/label/list")
  public Result<List<LibraryLabelVO>> labels(@RequestBody LibraryQueryDTO dto) {
    return Result.success(libraryService.labels(dto));
  }

  /** 新建/更新标签（body 带 id 为更新）。 */
  @Log(title = "标签库标签保存", businessType = BusinessType.UPDATE)
  @PostMapping("/label/save")
  public Result<LibraryLabelVO> saveLabel(@RequestBody LibraryLabelDTO dto) {
    return Result.success("保存成功", libraryService.saveLabel(dto));
  }

  /** 删除标签（body: id；软删）。 */
  @Log(title = "标签库标签删除", businessType = BusinessType.DELETE)
  @PostMapping("/label/delete")
  public Result<Void> deleteLabel(@RequestBody LibraryQueryDTO dto) {
    libraryService.deleteLabel(dto);
    return Result.success("删除成功", null);
  }

  // ---------------- 项目引入（复制语义） ----------------

  /** 项目引入·整集（视觉场景：库集 → 项目标签集，body: projectId + librarySetId + name?）。 */
  @Log(title = "项目引入标签集", businessType = BusinessType.INSERT)
  @PostMapping("/project/import")
  public Result<ClassSetVO> importSetToProject(@RequestBody LibraryImportDTO dto) {
    return Result.success("引入成功", libraryService.importSetToProject(dto));
  }

  /** 项目引入·任选组合（视觉场景：跨集勾选 → 项目标签集，body: projectId + name + libraryLabelIds）。 */
  @Log(title = "项目组合标签集", businessType = BusinessType.INSERT)
  @PostMapping("/project/compose")
  public Result<ClassSetVO> composeToProject(@RequestBody LibraryProjectComposeDTO dto) {
    return Result.success("组合成功", libraryService.composeToProject(dto));
  }

  /** 项目引入·整集（文字场景：库集 → 项目文本标签集，保留层级）。 */
  @Log(title = "项目引入文本标签集", businessType = BusinessType.INSERT)
  @PostMapping("/project/import-text")
  public Result<Long> importTextSetToProject(@RequestBody LibraryImportDTO dto) {
    return Result.success("引入成功", libraryService.importTextSetToProject(dto));
  }

  /** 项目引入·任选组合（文字场景：跨集勾选 → 项目文本标签集，保留层级）。 */
  @Log(title = "项目组合文本标签集", businessType = BusinessType.INSERT)
  @PostMapping("/project/compose-text")
  public Result<Long> composeTextToProject(@RequestBody LibraryProjectComposeDTO dto) {
    return Result.success("组合成功", libraryService.composeTextToProject(dto));
  }
}
