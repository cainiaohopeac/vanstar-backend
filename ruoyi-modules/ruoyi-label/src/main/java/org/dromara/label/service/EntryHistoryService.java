package org.dromara.label.service;

import java.util.Collection;
import java.util.List;
import org.dromara.label.vo.EntryHistoryVO;

/**
 * 条目操作历史：记录 + 查询。
 */
public interface EntryHistoryService {

  /** 记录单条条目的操作历史。 */
  void record(Long entryId, String action, String actionName, String nodeName, String detail);

  /** 批量记录条目操作历史（同一操作用于多条条目）。 */
  void recordBatch(Collection<Long> entryIds, String action, String actionName, String nodeName, String detail);

  /** 查询某条目的全部操作历史（时间正序）。 */
  List<EntryHistoryVO> listByEntryId(Long entryId);
}
