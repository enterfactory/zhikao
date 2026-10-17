package com.zhikao.modules.notification;

import com.zhikao.common.PageResult;
import com.zhikao.common.R;
import com.zhikao.modules.notification.vo.NotificationVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内通知接口。
 *
 * <p>没有写角色限制：三种角色都会收到通知，而「谁能看到哪些通知」
 * 由 {@code user_id} 天然隔离——查询条件里永远带上当前登录用户，
 * 不存在「管理员看所有人通知」的接口，所以不需要额外的角色判断。
 */
@RestController
@RequestMapping("/notification")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** 我的通知（分页）；unreadOnly=1 时只看未读 */
    @GetMapping("/page")
    public R<PageResult<NotificationVO>> page(@RequestParam(defaultValue = "1") int pageNum,
                                              @RequestParam(defaultValue = "10") int pageSize,
                                              @RequestParam(defaultValue = "0") int unreadOnly) {
        return R.ok(notificationService.page(pageNum, pageSize, unreadOnly == 1));
    }

    /**
     * 未读数量。
     * 前端轮询这一个数字来决定要不要亮红点，比轮询整页列表便宜得多。
     */
    @GetMapping("/unread-count")
    public R<Integer> unreadCount() {
        return R.ok(notificationService.unreadCount());
    }

    /** 标记单条已读 */
    @PutMapping("/{id}/read")
    public R<Void> read(@PathVariable Long id) {
        notificationService.read(id);
        return R.ok();
    }

    /** 全部标记已读 */
    @PostMapping("/read-all")
    public R<Void> readAll() {
        notificationService.readAll();
        return R.ok();
    }
}
