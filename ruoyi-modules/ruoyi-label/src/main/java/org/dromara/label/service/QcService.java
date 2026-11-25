package org.dromara.label.service;

import org.dromara.label.common.PageResult;
import org.dromara.label.dto.QcRuleSaveDTO;
import org.dromara.label.vo.QcReportVO;
import org.dromara.label.vo.QcRuleVO;

/**
 * 质检域服务：质检报告列表/详情、质检规则维护、作业抽检。
 */
public interface QcService {

  /** 质检报告列表。 */
  PageResult<QcReportVO> qcReports();

  /** 质检报告详情。 */
  QcReportVO qcReport(Long id);

  /** 质检规则列表。 */
  PageResult<QcRuleVO> qcRules();

  /** 创建或更新质检规则。 */
  QcRuleVO saveQcRule(QcRuleSaveDTO dto);

  /** 对作业发起抽样质检。 */
  QcReportVO sampleQcJob(Long jobId);
}
