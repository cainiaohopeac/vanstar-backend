package org.dromara.label.controller;

import org.dromara.label.common.PageResult;
import org.dromara.label.common.Result;
import org.dromara.label.service.NotificationService;
import org.dromara.label.vo.CountVO;
import org.dromara.label.vo.NotificationVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

/** 通知域。 */
@RestController
public class NotificationController {

  @Autowired
  private NotificationService notificationService;

  @GetMapping("/notifications")
  public Result<PageResult<NotificationVO>> notifications() {
    return Result.success(notificationService.notifications());
  }

  @GetMapping("/notifications/unread-count")
  public Result<CountVO> unreadNotificationCount() {
    return Result.success(notificationService.unreadNotificationCount());
  }

  @PutMapping("/notifications/{id}/read")
  public Result<NotificationVO> readNotification(@PathVariable Long id) {
    return Result.success("已读", notificationService.readNotification(id));
  }

  @PutMapping("/notifications/read-all")
  public Result<Void> readAllNotifications() {
    notificationService.readAllNotifications();
    return Result.success("已全部标记", null);
  }
}
