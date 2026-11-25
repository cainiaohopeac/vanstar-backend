package org.dromara.label.controller;

import org.dromara.label.common.PageResult;
import org.dromara.label.common.Result;
import org.dromara.label.dto.ExportCreateDTO;
import org.dromara.label.service.ExportService;
import org.dromara.label.vo.DeliveryRecordVO;
import org.dromara.label.vo.ExportBatchVO;
import org.dromara.label.vo.ExportDownloadVO;
import org.dromara.label.vo.ExportJobVO;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 导出交付域。 */
@RestController
public class ExportController {

  @Autowired
  private ExportService exportService;

  @Log(title = "数据集导出", businessType = BusinessType.EXPORT)
  @PostMapping("/exports")
  public Result<ExportJobVO> createExport(@RequestBody ExportCreateDTO dto) {
    return Result.success("导出任务已创建", exportService.createExport(dto));
  }

  @GetMapping("/exports")
  public Result<PageResult<ExportJobVO>> exports() {
    return Result.success(exportService.exports());
  }

  /** 项目下可导出（已完成：批次内全部条目合格）的批次列表，供创建导出时选择。 */
  @GetMapping("/exports/batches")
  public Result<List<ExportBatchVO>> exportBatches(@RequestParam("projectId") Long projectId) {
    return Result.success(exportService.exportBatches(projectId));
  }

  @GetMapping("/exports/{id}")
  public Result<ExportJobVO> export(@PathVariable Long id) {
    return Result.success(exportService.export(id));
  }

  @Log(title = "取消导出", businessType = BusinessType.UPDATE)
  @PostMapping("/exports/{id}/cancel")
  public Result<ExportJobVO> cancelExport(@PathVariable Long id) {
    return Result.success("已取消", exportService.cancelExport(id));
  }

  @Log(title = "导出文件下载", businessType = BusinessType.EXPORT)
  @GetMapping("/exports/{id}/download")
  public Result<ExportDownloadVO> downloadExport(@PathVariable Long id) {
    return Result.success(exportService.downloadExport(id));
  }

  @GetMapping("/delivery-records")
  public Result<PageResult<DeliveryRecordVO>> deliveryRecords() {
    return Result.success(exportService.deliveryRecords());
  }
}
