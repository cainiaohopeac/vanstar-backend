package org.dromara.label.controller;

import java.util.List;
import org.dromara.label.common.Result;
import org.dromara.label.service.SupportService;
import org.dromara.label.vo.LabelUserVO;
import org.dromara.label.vo.OperationLogVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 支持域（用户候选下拉、操作日志、登录日志占位）。 */
@RestController
public class SupportController {

  @Autowired
  private SupportService supportService;

  @GetMapping("/users")
  public Result<List<LabelUserVO>> users(
      @RequestParam(value = "keyword", required = false) String keyword,
      @RequestParam(value = "roleKey", required = false) String roleKey) {
    return Result.success(supportService.labelUsers(keyword, roleKey));
  }

  @GetMapping("/logs/operation")
  public Result<List<OperationLogVO>> operationLogs() {
    return Result.success(supportService.operationLogs());
  }

  @GetMapping("/logs/login")
  public Result<List<OperationLogVO>> loginLogs() {
    return Result.success(List.of());
  }
}
