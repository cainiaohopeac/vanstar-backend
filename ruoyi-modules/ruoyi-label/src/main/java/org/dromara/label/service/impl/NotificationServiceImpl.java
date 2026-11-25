package org.dromara.label.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.label.common.PageResult;
import org.dromara.label.mapper.NotificationMapper;
import org.dromara.label.service.BaseService;
import org.dromara.label.service.NotificationService;
import org.dromara.label.vo.CountVO;
import org.dromara.label.vo.NotificationVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 通知域服务：站内消息列表/未读数/单条已读/全部已读。
 * SQL 统一收敛至 NotificationMapper.xml。
 */
@Service
public class NotificationServiceImpl extends BaseService implements NotificationService {

  @Autowired
  private NotificationMapper notificationMapper;

  @Override
  public PageResult<NotificationVO> notifications() {
    return PageResult.of(notificationMapper.selectNotifications(currentUserId()));
  }

  @Override
  public CountVO unreadNotificationCount() {
    return new CountVO(notificationMapper.countUnread(currentUserId()));
  }

  @Override
  public NotificationVO readNotification(Long id) {
    notificationMapper.updateRead(id);
    NotificationVO notification = notificationMapper.selectNotification(id);
    if (notification == null) {
      throw new ServiceException("消息不存在");
    }
    return notification;
  }

  @Override
  public void readAllNotifications() {
    notificationMapper.updateAllRead(currentUserId());
  }
}
