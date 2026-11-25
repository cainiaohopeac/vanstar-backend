package org.dromara.label.controller;

import jakarta.validation.Valid;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.label.common.Result;
import org.dromara.label.dto.NuscenesParseDTO;
import org.dromara.label.service.NuscenesImportService;
import org.dromara.label.vo.NuscenesParseResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * nuScenes 数据集导入接口。
 *
 * <p>上传链路会在检测到 v1.0-* 元数据时自动解析（建点云-相机关联 + 预标注）；
 * 本接口提供自动触发失败后的手动重试入口。入参为实体（POST body），无路径参数。</p>
 */
@RestController
public class NuscenesController {

  @Autowired
  private NuscenesImportService nuscenesImportService;

  /** 手动触发一条上传数据的 nuScenes 元数据解析（相机图关联 + 自动预标注）。 */
  @Log(title = "nuScenes元数据解析", businessType = BusinessType.IMPORT)
  @PostMapping("/nuscenes/parse")
  public Result<NuscenesParseResultVO> parse(@Valid @RequestBody NuscenesParseDTO dto) {
    return Result.success("解析完成", nuscenesImportService.parseUpload(dto));
  }
}
