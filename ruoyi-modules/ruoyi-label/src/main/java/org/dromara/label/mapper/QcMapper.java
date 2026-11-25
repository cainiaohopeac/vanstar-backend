package org.dromara.label.mapper;

import org.apache.ibatis.annotations.Param;
import org.dromara.label.vo.QcReportVO;
import org.dromara.label.vo.QcRuleVO;

import java.util.List;
import java.util.Map;

/**
 * 质检域数据访问（label_qc_report/label_qc_rule/label_job）
 */
public interface QcMapper {

    // ---------------- 质检报告 ----------------

    List<QcReportVO> selectQcReports();

    QcReportVO selectQcReport(@Param("id") Long id);

    int insertQcReport(Map<String, Object> p);

    Long selectJobProjectId(@Param("jobId") Long jobId);

    // ---------------- 质检规则 ----------------

    List<QcRuleVO> selectQcRules();

    QcRuleVO selectQcRule(@Param("id") Long id);

    int insertQcRule(Map<String, Object> p);

    int updateQcRule(Map<String, Object> p);
}
