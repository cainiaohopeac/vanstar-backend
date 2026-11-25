package org.dromara.label.service;

import java.util.List;
import org.dromara.label.vo.LabelUserVO;
import org.dromara.label.vo.OperationLogVO;

/**
 * 支持域服务：用户候选下拉、系统操作日志等平台公共辅助查询。
 */
public interface SupportService {

  /** 用户候选(项目负责人/成员等下拉选择)；按用户名/昵称关键字与角色过滤（roleKey 为空表示不限角色）。 */
  List<LabelUserVO> labelUsers(String keyword, String roleKey);

  /** 平台操作日志（含目标对象）。 */
  List<OperationLogVO> operationLogs();
}
