package com.zhikao.modules.notification;

import lombok.Getter;

/**
 * 通知类型。
 *
 * <p>写死成一个枚举而不是随手传字符串，是因为通知的类型决定了
 * 「前端怎么展示图标、点击后跳哪儿」——散落的裸字符串迟早会出现
 * 拼错一个字母然后静默落到默认分支的情况，而那种 bug 没有报错、只能被用户发现。
 */
@Getter
public enum NotificationTypeEnum {

    /** 考试已发布 */
    EXAM_PUBLISH("exam_publish", "考试发布", "message"),
    /** 考试公告（教师追加的通知） */
    EXAM_ANNOUNCEMENT("exam_announcement", "考试公告", "bell"),
    /** 成绩已公布（主观题批改完成） */
    GRADE_PUBLISHED("grade_published", "成绩公布", "data-line"),
    /** AI 任务完成 */
    AI_TASK("ai_task", "AI 任务", "magic-stick");

    private final String code;
    private final String label;
    /** 前端图标名（Element-Plus 图标组件名） */
    private final String icon;

    NotificationTypeEnum(String code, String label, String icon) {
        this.code = code;
        this.label = label;
        this.icon = icon;
    }

    /** 按 code 解析，未知类型回落到考试公告而不是抛异常——通知不该因为它本身而失败 */
    public static NotificationTypeEnum of(String code) {
        for (NotificationTypeEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return EXAM_ANNOUNCEMENT;
    }
}
