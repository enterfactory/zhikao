package com.zhikao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 站内通知。
 *
 * <p><b>为什么明细必须落库：</b>
 * 通知不是「缓存能算出来的东西」，它是给用户看的历史记录——
 * 清空 Redis 后用户还得能看到上周发过什么。这和限流计数正好相反：
 * 计数丢了无所谓（重新计），通知丢了就是永久消失。
 *
 * <p><b>Redis 在这里只负责一件小事：</b>
 * 未读数被每次页面刷新都查一次「有多少人关心」，用 Redis 存一份带 TTL 的计数，
 * 写操作后直接删掉这个 key 让下次回源，比对计数做增减更不容易算错。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_notification")
public class SysNotification {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收人 id */
    private Long userId;

    /** 通知类型，见 NotificationTypeEnum */
    private String type;

    private String title;

    private String content;

    /** 关联业务类型：exam / grade */
    private String bizType;

    /** 关联业务 id，点击通知跳转用 */
    private Long bizId;

    /** 0 未读 1 已读 */
    private Integer readFlag;

    private LocalDateTime createTime;

    private LocalDateTime readTime;
}
