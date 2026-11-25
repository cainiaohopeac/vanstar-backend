package org.dromara.label.service.impl;

import java.util.HashMap;
import java.util.Map;
import org.dromara.label.common.PageResult;
import org.dromara.label.dto.QcRuleSaveDTO;
import org.dromara.label.mapper.QcMapper;
import org.dromara.label.service.BaseService;
import org.dromara.label.service.QcService;
import org.dromara.label.service.TaskService;
import org.dromara.label.vo.JobVO;
import org.dromara.label.vo.QcReportVO;
import org.dromara.label.vo.QcRuleVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 质检域服务：质检报告列表/详情、质检规则维护、作业抽检。
 * SQL 统一收敛至 QcMapper.xml。
 */
@Service
public class QcServiceImpl extends BaseService implements QcService {

  @Autowired
  private QcMapper qcMapper;

  @Autowired
  private TaskService taskService;

  /** 质检报告列表。 */
  @Override
  public PageResult<QcReportVO> qcReports() {
    return PageResult.of(qcMapper.selectQcReports());
  }

  @Override
  public QcReportVO qcReport(Long id) {
    QcReportVO report = qcMapper.selectQcReport(id);
    if (report == null) {
      throw new org.dromara.common.core.exception.ServiceException("质检报告不存在");
    }
    return report;
  }

  /** 质检规则列表。 */
  @Override
  public PageResult<QcRuleVO> qcRules() {
    return PageResult.of(qcMapper.selectQcRules());
  }

  /** 新建/编辑质检规则。 */
  @Override
  @Transactional
  public QcRuleVO saveQcRule(QcRuleSaveDTO dto) {
    Long id = dto.getId();
    Long projectId = findProjectId(dto.getProjectName(), 1L);
    Long taskId = findTaskId(dto.getTaskName());
    Number sampleRate = dto.getSampleRate() == null ? 20 : dto.getSampleRate();
    int minSampleCount = dto.getMinSampleCount() == null ? 1 : dto.getMinSampleCount();
    String status = dto.getStatus() == null || dto.getStatus().isBlank() ? "active" : dto.getStatus();
    long userId = currentUserId();
    if (id == null) {
      Map<String, Object> p = new HashMap<>();
      p.put("projectId", projectId);
      p.put("taskId", taskId);
      p.put("sampleRate", sampleRate);
      p.put("minSampleCount", minSampleCount);
      p.put("status", status);
      p.put("userId", userId);
      qcMapper.insertQcRule(p);
      id = asLong(p.get("id"));
    } else {
      Map<String, Object> p = new HashMap<>();
      p.put("id", id);
      p.put("projectId", projectId);
      p.put("taskId", taskId);
      p.put("sampleRate", sampleRate);
      p.put("minSampleCount", minSampleCount);
      p.put("status", status);
      p.put("userId", userId);
      qcMapper.updateQcRule(p);
    }
    writeOperationLog("SAVE_QC_RULE", "qc_rule", id, Map.of("sampleRate", sampleRate));
    QcRuleVO rule = qcMapper.selectQcRule(id);
    if (rule == null) {
      throw new org.dromara.common.core.exception.ServiceException("质检规则不存在");
    }
    return rule;
  }

  /** 作业抽检：按 20% 抽样率生成质检报告（MVP 简化统计）。 */
  @Override
  @Transactional
  public QcReportVO sampleQcJob(Long jobId) {
    JobVO job = taskService.job(jobId);
    Long projectId = qcMapper.selectJobProjectId(jobId);
    Long taskId = job.getTaskId();
    int assetCount = job.getAssetCount() == null ? 0 : job.getAssetCount();
    int rejectCount = job.getRejectCount() == null ? 0 : job.getRejectCount();
    int sampleCount = Math.max(1, (int) Math.ceil(assetCount * 0.2));
    int passCount = Math.max(0, sampleCount - rejectCount);
    int actualRejectCount = sampleCount - passCount;
    int passRate = sampleCount == 0 ? 0 : (int) Math.round(passCount * 100.0 / sampleCount);
    Map<String, Object> p = new HashMap<>();
    p.put("projectId", projectId);
    p.put("taskId", taskId);
    p.put("sampleCount", sampleCount);
    p.put("passCount", passCount);
    p.put("rejectCount", actualRejectCount);
    p.put("passRate", passRate);
    p.put("problemSummaryJson", toJson(Map.of("jobNo", job.getJobNo(), "sampleType", "job_manual")));
    p.put("userId", currentUserId());
    qcMapper.insertQcReport(p);
    long reportId = asLong(p.get("id"));
    writeOperationLog("SAMPLE_QC_JOB", "job", jobId, Map.of("sampleCount", sampleCount));
    return qcReport(reportId);
  }
}
