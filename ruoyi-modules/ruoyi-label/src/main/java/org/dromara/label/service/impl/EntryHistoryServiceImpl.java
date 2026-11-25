package org.dromara.label.service.impl;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.dromara.label.mapper.EntryHistoryMapper;
import org.dromara.label.service.BaseService;
import org.dromara.label.service.EntryHistoryService;
import org.dromara.label.vo.EntryHistoryVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 条目操作历史服务实现。
 *
 * <p>历史写入属于「附加审计」，不应影响主业务事务的成功与否：
 * 因此内部吞掉异常并打日志（例如升级脚本未执行、表不存在时业务仍可正常流转）。</p>
 */
@Slf4j
@Service
public class EntryHistoryServiceImpl extends BaseService implements EntryHistoryService {

  @Autowired
  private EntryHistoryMapper entryHistoryMapper;

  @Override
  public void record(Long entryId, String action, String actionName, String nodeName, String detail) {
    if (entryId == null) {
      return;
    }
    recordBatch(List.of(entryId), action, actionName, nodeName, detail);
  }

  @Override
  public void recordBatch(Collection<Long> entryIds, String action, String actionName, String nodeName,
      String detail) {
    if (entryIds == null || entryIds.isEmpty() || action == null || action.isBlank()) {
      return;
    }
    List<Long> ids = entryIds.stream()
        .filter(Objects::nonNull)
        .distinct()
        .collect(Collectors.toList());
    if (ids.isEmpty()) {
      return;
    }
    try {
      entryHistoryMapper.insertBatch(ids, action, truncate(actionName, 64), truncate(nodeName, 64),
          truncate(detail, 500), currentUserId());
    } catch (Exception e) {
      // 历史落库失败不影响主操作（如表未建、DDL 未执行）
      log.warn("写入条目操作历史失败 action={} entryIds={}", action, ids, e);
    }
  }

  @Override
  public List<EntryHistoryVO> listByEntryId(Long entryId) {
    if (entryId == null) {
      return List.of();
    }
    return entryHistoryMapper.selectByEntryId(entryId);
  }

  /** 截断超长文本，避免超出表列长度导致写入失败。 */
  private String truncate(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength);
  }
}
