package org.dromara.label.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.dromara.label.vo.EntryHistoryVO;

/**
 * 条目操作历史 Mapper。
 *
 * <p>插入时直接从 label_data_entry 带出 task_id / batch_id，避免调用方逐个反查。</p>
 */
public interface EntryHistoryMapper {

  /**
   * 批量写入条目操作历史（entryId 取自 label_data_entry，task_id / batch_id 由条目带出）。
   */
  int insertBatch(@Param("entryIds") List<Long> entryIds,
      @Param("action") String action,
      @Param("actionName") String actionName,
      @Param("nodeName") String nodeName,
      @Param("detail") String detail,
      @Param("operatorId") Long operatorId);

  /**
   * 查询某条目的全部操作历史（按时间正序，便于前端时间线展示）。
   */
  List<EntryHistoryVO> selectByEntryId(@Param("entryId") Long entryId);
}
