package org.dromara.label.mapper;

import org.apache.ibatis.annotations.Param;
import org.dromara.label.vo.LabelUserVO;
import org.dromara.label.vo.OperationLogVO;

import java.util.List;

/**
 * 支持域数据访问（sys_user/sys_operation_log）
 */
public interface SupportMapper {

    /** 用户候选：按用户名/昵称关键字与角色 roleKey 过滤（roleKey 为空表示不限角色） */
    List<LabelUserVO> selectUsers(@Param("keyword") String keyword, @Param("roleKey") String roleKey);

    List<OperationLogVO> selectOperationLogs();
}
