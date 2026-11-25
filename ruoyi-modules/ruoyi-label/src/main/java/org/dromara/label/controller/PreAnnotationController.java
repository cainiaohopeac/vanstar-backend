package org.dromara.label.controller;

import org.dromara.label.common.Result;
import org.dromara.label.service.PreAnnotationService;
import org.dromara.label.vo.PreAnnotationResultVO;
import org.dromara.label.vo.PreAnnotationVO;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 预标注域：解析外部标注文件（nuScenes 压缩包 / COCO / YOLO / 平台点位导出），
 * 生成平台预标注结果并关联数据资产。格式由 {@code format} 参数指定，缺省为 nuscenes。
 */
@RestController
public class PreAnnotationController {

  @Autowired
  private PreAnnotationService preAnnotationService;

  /** 对整条上传数据执行预标注（匹配该上传数据下的数据资产）。 */
  @Log(title = "上传数据预标注", businessType = BusinessType.IMPORT, isSaveRequestData = false)
  @PostMapping(value = "/pre-annotations/uploads/{uploadId}",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Result<PreAnnotationResultVO> preAnnotateUpload(@PathVariable Long uploadId,
                                                         @RequestParam("file") MultipartFile file,
                                                         @RequestParam(value = "format", required = false) String format) {
    return Result.success("预标注完成", preAnnotationService.preAnnotateUpload(uploadId, file, format));
  }

  /** 对单个数据资产执行预标注。 */
  @Log(title = "数据资产预标注", businessType = BusinessType.IMPORT, isSaveRequestData = false)
  @PostMapping(value = "/pre-annotations/assets/{assetId}",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Result<PreAnnotationResultVO> preAnnotateAsset(@PathVariable Long assetId,
                                                        @RequestParam("file") MultipartFile file,
                                                        @RequestParam(value = "format", required = false) String format) {
    return Result.success("预标注完成", preAnnotationService.preAnnotateAsset(assetId, file, format));
  }

  /** 查询数据资产的预标注结果（含预标注框）。 */
  @GetMapping("/pre-annotations/assets/{assetId}")
  public Result<PreAnnotationVO> preAnnotation(@PathVariable Long assetId) {
    return Result.success(preAnnotationService.preAnnotation(assetId));
  }
}
