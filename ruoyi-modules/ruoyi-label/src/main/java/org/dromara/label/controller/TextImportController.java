package org.dromara.label.controller;

import jakarta.validation.Valid;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.label.common.Result;
import org.dromara.label.dto.TextImportDTO;
import org.dromara.label.service.TextImportService;
import org.dromara.label.vo.TextImportResultVO;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文本数据导入接口（txt / docx / json / csv，单文件 ≤ 20MB）。
 *
 * <p>文件本身只能走 multipart，其余参数收进 {@link TextImportDTO} 一并提交，
 * 避免接口签名变成一长串 {@code @RequestParam}。
 */
@RestController
@RequestMapping("/label/text/import")
public class TextImportController {

  private final TextImportService textImportService;

  public TextImportController(TextImportService textImportService) {
    this.textImportService = textImportService;
  }

  /**
   * 导入一个文本文件，生成一个条目 + 一个资产，并解析出句边界。
   */
  @Log(title = "文本数据导入", businessType = BusinessType.INSERT, isSaveRequestData = false)
  @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Result<TextImportResultVO> importFile(
      @RequestPart("file") MultipartFile file,
      @Valid @RequestPart("meta") TextImportDTO meta) {
    return Result.success("导入成功", textImportService.importFile(meta, file));
  }
}
