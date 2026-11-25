package org.dromara.label.mapper;

import org.apache.ibatis.annotations.Param;
import org.dromara.label.vo.NotificationVO;

import java.util.List;

/**
 * 通知域数据访问（sys_notification）
 */
public interface NotificationMapper {

    List<NotificationVO> selectNotifications(@Param("userId") Long userId);

    NotificationVO selectNotification(@Param("id") Long id);

    long countUnread(@Param("userId") Long userId);

    int updateRead(@Param("id") Long id);

    int updateAllRead(@Param("userId") Long userId);
}
