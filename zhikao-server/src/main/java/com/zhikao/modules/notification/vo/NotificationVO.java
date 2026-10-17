package com.zhikao.modules.notification.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知视图。
 *
 * <p>带上 typeLabel / icon 是为了让前端不必再维护一份「类型到文案」的映射——
 * 只要类型字典在前端和后端各写一份，就一定会有某一端忘记同步。
 */
@Data
public class NotificationVO {

    private Long id;
    private String type;
    private String typeLabel;
    private String icon;
    private String title;
    private String content;
    private String bizType;
    private Long bizId;
    private Integer readFlag;
    private LocalDateTime createTime;
    private LocalDateTime readTime;
}
