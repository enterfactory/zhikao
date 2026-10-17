package com.zhikao.modules.notification;

import com.zhikao.common.PageResult;
import com.zhikao.modules.notification.vo.NotificationVO;

import java.util.List;

/**
 * 站内通知服务。
 *
 * <p>两类方法分开声明是有意的：
 * <ul>
 *   <li>{@code send*} 是给<b>其它业务模块</b>用的写口子，业务不需要关心存储细节；</li>
 *   <li>{@code page / unreadCount / read*} 是给<b>当前用户</b>读自己通知的口子。</li>
 * </ul>
 * 读写之间只有一条数据流向：业务 → 通知，不存在「通知反过来改业务」。
 */
public interface NotificationService {

    /** 本人的通知分页；unreadOnly=true 时只看未读 */
    PageResult<NotificationVO> page(int pageNum, int pageSize, boolean unreadOnly);

    /** 本人未读数（带 Redis 缓存，写操作后自动失效） */
    int unreadCount();

    /** 标记某条已读。只能标记自己的——越权 ID 直接按不存在处理 */
    void read(Long id);

    /** 全部标记已读 */
    void readAll();

    /**
     * 给一批用户发通知。
     *
     * @param userIds 接收人列表；为空时直接返回（教师发了一场没有学生的班级，不该报错）
     */
    void send(List<Long> userIds, NotificationTypeEnum type, String title, String content,
              String bizType, Long bizId);

    /** 给单个用户发通知的便捷方法 */
    void sendOne(Long userId, NotificationTypeEnum type, String title, String content,
                 String bizType, Long bizId);
}
