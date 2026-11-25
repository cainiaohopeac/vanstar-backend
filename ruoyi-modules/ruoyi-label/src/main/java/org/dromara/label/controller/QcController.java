package org.dromara.label.controller;

import org.dromara.label.common.PageResult;
import org.dromara.label.common.Result;
import org.dromara.label.dto.QcRuleSaveDTO;
import org.dromara.label.service.QcService;
import org.dromara.label.vo.QcReportVO;
import org.dromara.label.vo.QcRuleVO;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 质检域。 */
@RestController
public class QcController {

  @Autowired
  private QcService qcService;

  @GetMapping("/qc/reports")
  public Result<PageResult<QcReportVO>> qcReports() {
    return Result.success(qcService.qcReports());
  }

  @GetMapping("/qc/reports/{id}")
  public Result<QcReportVO> qcReport(@PathVariable Long id) {
    return Result.success(qcService.qcReport(id));
  }

  @GetMapping("/qc/rules")
  public Result<PageResult<QcRuleVO>> qcRules() {
    return Result.success(qcService.qcRules());
  }

  @Log(title = "质检规则", businessType = BusinessType.INSERT)
  @PostMapping("/qc/rules")
  public Result<QcRuleVO> createQcRule(@RequestBody QcRuleSaveDTO dto) {
    return Result.success("创建成功", qcService.saveQcRule(dto));
  }

  @Log(title = "质检规则", businessType = BusinessType.UPDATE)
  @PutMapping("/qc/rules/{id}")
  public Result<QcRuleVO> updateQcRule(@PathVariable Long id, @RequestBody QcRuleSaveDTO dto) {
    dto.setId(id);
    return Result.success("保存成功", qcService.saveQcRule(dto));
  }

  @Log(title = "作业抽检", businessType = BusinessType.INSERT)
  @PostMapping("/qc/jobs/{id}/sample")
  public Result<QcReportVO> sampleQcJob(@PathVariable Long id) {
    return Result.success("抽检已生成", qcService.sampleQcJob(id));
  }
}
