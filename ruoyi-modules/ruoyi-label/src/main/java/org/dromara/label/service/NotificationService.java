package org.dromara.label.service;

import org.dromara.label.common.PageResult;
import org.dromara.label.vo.CountVO;
import org.dromara.label.vo.NotificationVO;

/**
 * 通知域服务：站内消息列表/未读数/单条已读/全部已读。
 */
public interface NotificationService {

  /** 当前用户站内消息列表。 */
  PageResult<NotificationVO> notifications();

  /** 当前用户未读消息数。 */
  CountVO unreadNotificationCount();

  /** 标记单条消息已读。 */
  NotificationVO readNotification(Long id);

  /** 全部标记已读。 */
  void readAllNotifications();
}
