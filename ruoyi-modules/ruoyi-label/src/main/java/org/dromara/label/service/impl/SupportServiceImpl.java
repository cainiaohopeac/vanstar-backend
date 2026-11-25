package org.dromara.label.service.impl;

import java.util.List;
import org.dromara.label.mapper.SupportMapper;
import org.dromara.label.service.BaseService;
import org.dromara.label.service.SupportService;
import org.dromara.label.vo.LabelUserVO;
import org.dromara.label.vo.OperationLogVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 支持域服务：用户候选下拉、系统操作日志等平台公共辅助查询。
 * SQL 统一收敛至 SupportMapper.xml。
 */
@Service
public class SupportServiceImpl extends BaseService implements SupportService {

  @Autowired
  private SupportMapper supportMapper;

  /**
   * 用户候选(项目负责人/团队负责人/成员等下拉选择)。
   * 仅返回启用账号, 支持按用户名/昵称关键字过滤；
   * 传入 roleKey 时仅返回拥有该角色的人员（如 team_leader / project_leader）。
   */
  @Override
  public List<LabelUserVO> labelUsers(String keyword, String roleKey) {
    return supportMapper.selectUsers(keyword, roleKey);
  }

  /** 平台操作日志（含目标对象）。 */
  @Override
  public List<OperationLogVO> operationLogs() {
    return supportMapper.selectOperationLogs();
  }
}
